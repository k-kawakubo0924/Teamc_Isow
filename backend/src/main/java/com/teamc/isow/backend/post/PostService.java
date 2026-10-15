package com.teamc.isow.backend.post;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.block.UserVisibility;
import com.teamc.isow.backend.auth.UnknownTokenUserException;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.image.ImageUploadService;
import com.teamc.isow.backend.image.ImageUploadService.PreparedImage;
import com.teamc.isow.backend.image.InvalidImageException;
import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.reaction.ReactionSummary;
import com.teamc.isow.backend.reaction.ReactionSummaryService;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagService;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿の作成・取得・編集・削除。
 *
 * <p>作成時、画像の保存（ファイル）は DB のトランザクションに含められないため、次の順に進め、途中で失敗しても中途半端な状態を残さない。
 * <ol>
 *   <li>すべての入力を確認する（ここではまだ何も保存しない）</li>
 *   <li>画像を保存する。途中で失敗したら、保存済みの画像を削除する</li>
 *   <li>1つのトランザクションでタグ・投稿を登録する。失敗したら DB はロールバックし、保存した画像を削除する</li>
 * </ol>
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private static final String FASHION_CATEGORY_MESSAGE = "ファッションの種類を選択してください";

    /** 投稿一覧の1ページあたりの件数の上限 */
    static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final FashionCategoryRepository fashionCategoryRepository;
    private final TagService tagService;
    private final ImageUploadService imageUploadService;
    private final AuthService authService;
    private final ReactionSummaryService reactionSummaryService;
    private final UserVisibility userVisibility;
    private final TransactionTemplate transactionTemplate;

    public PostService(
            PostRepository postRepository,
            UserRepository userRepository,
            FashionCategoryRepository fashionCategoryRepository,
            TagService tagService,
            ImageUploadService imageUploadService,
            AuthService authService,
            ReactionSummaryService reactionSummaryService,
            UserVisibility userVisibility,
            PlatformTransactionManager transactionManager) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.fashionCategoryRepository = fashionCategoryRepository;
        this.tagService = tagService;
        this.imageUploadService = imageUploadService;
        this.authService = authService;
        this.reactionSummaryService = reactionSummaryService;
        this.userVisibility = userVisibility;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 投稿を作成する。投稿者はトークンの sub（ログイン中のユーザー）。
     * request はアノテーションによる入力チェック済みであること。
     *
     * @throws InputValidationException 選択肢・タグ・画像が条件を満たさない場合（何も保存しない）
     */
    public PostResponse create(String subject, PostCreateRequest request) {
        Long authorId = authService.requireCurrentUser(subject).getId();

        // 1. 画像を保存してから入力の誤りに気づくことがないよう、先にすべてを確認する
        Map<String, String> errors = new LinkedHashMap<>();
        if (findActiveFashionCategory(request.fashionCategoryId()).isEmpty()) {
            errors.put("fashionCategoryId", FASHION_CATEGORY_MESSAGE);
        }
        List<String> tagNames = List.of();
        try {
            tagNames = tagService.validateForPost(request.tags());
        } catch (InputValidationException e) {
            errors.putAll(e.getErrors());
        }
        List<PreparedImage> images = new ArrayList<>();
        List<MultipartFile> files = request.images();
        for (int i = 0; i < files.size(); i++) {
            try {
                images.add(imageUploadService.prepare(files.get(i)));
            } catch (InvalidImageException e) {
                errors.put("images", (i + 1) + "枚目の写真：" + e.getMessage());
                break;
            }
        }
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        // 2. 画像を保存する
        List<String> imageUrls = storeImages(images);

        // 3. DB に登録する。失敗したら、保存した画像を削除する
        try {
            return saveWithRetry(authorId, request, tagNames, imageUrls);
        } catch (RuntimeException e) {
            imageUploadService.deleteQuietly(imageUrls);
            throw e;
        }
    }

    /** 画像をすべて保存する。途中で失敗したら、それまでに保存した画像を削除する */
    private List<String> storeImages(List<PreparedImage> images) {
        List<String> urls = new ArrayList<>();
        try {
            for (PreparedImage image : images) {
                urls.add(imageUploadService.store(image));
            }
            return urls;
        } catch (RuntimeException e) {
            imageUploadService.deleteQuietly(urls);
            throw e;
        }
    }

    /**
     * 同じ新しいタグが別の投稿で同時に作られると、一意制約違反になる。
     * その場合は1回だけやり直す（やり直すと、相手が作ったタグが既存のタグとして見つかる）
     */
    private PostResponse saveWithRetry(Long authorId, PostCreateRequest request, List<String> tagNames, List<String> imageUrls) {
        try {
            return transactionTemplate.execute(status -> save(authorId, request, tagNames, imageUrls));
        } catch (DataIntegrityViolationException e) {
            log.info("投稿の登録で一意制約違反が発生したため、やり直します: {}", e.getMostSpecificCause().getMessage());
            return transactionTemplate.execute(status -> save(authorId, request, tagNames, imageUrls));
        }
    }

    /** トランザクションの中で呼ぶ。確認の後に状態が変わっている場合（選択肢が無効にされたなど）は、ここでエラーにする */
    private PostResponse save(Long authorId, PostCreateRequest request, List<String> tagNames, List<String> imageUrls) {
        User author = userRepository.findById(authorId).orElseThrow(UnknownTokenUserException::new);
        FashionCategory fashionCategory = findActiveFashionCategory(request.fashionCategoryId())
                .orElseThrow(() -> InputValidationException.of("fashionCategoryId", FASHION_CATEGORY_MESSAGE));
        List<Tag> tags = tagService.findOrCreateForPost(tagNames);

        Post post = postRepository.save(new Post(
                author,
                request.title(),
                fashionCategory,
                request.wornItems(),
                request.description(),
                request.referenceUrl(),
                imageUrls,
                tags));
        // 作成した直後のため、いいね・お気に入りはまだない
        return PostResponse.from(post, ReactionSummary.NONE, authorId);
    }

    /**
     * 投稿1件の詳細（docs/post.md「投稿の詳細画面」）。ログインしていれば誰の投稿でも取得できる
     * （公開範囲は docs/post.md で未確定のため全体公開として扱う）。
     *
     * <p>写真の枚数・タグの数に関係なく、決まった本数（8本）の SQL で取得する：ログイン確認1、
     * 投稿・投稿者・ファッションの種類1、ブロックの確認1、写真1、タグ1、いいね・お気に入り3
     *
     * @throws PostNotFoundException 投稿が存在しない場合
     */
    @Transactional(readOnly = true)
    public PostResponse get(String subject, Long id) {
        Long userId = authService.requireCurrentUser(subject).getId();
        Post post = postRepository.findDetailById(id).orElseThrow(() -> new PostNotFoundException(id));
        // 投稿者とブロックの間柄にある投稿は、存在しない投稿と同じ 404（docs/profile.md「ブロック」）
        userVisibility.requireVisiblePost(userId, post);
        return PostResponse.from(post, reactionSummaryService.summarize(userId, List.of(id)).get(id), userId);
    }

    /**
     * 投稿の文字の情報を編集する（docs/post.md「投稿の編集」）。編集できるのは投稿者本人だけ。写真は変えない。
     * request はアノテーションによる入力チェック済みであること。
     *
     * <p>無効にされたファッションの種類・タグは、この投稿にすでに設定されているものに限り、そのまま保存できる
     * （プロフィールの編集と同じ。docs/admin.md「無効にしたときの利用者側への影響」）。
     * 「すでに設定されているか」は、クライアントから送られた値ではなく、DB から読んだ投稿の値で判定する。
     *
     * <p>同じ新しいタグが別の投稿で同時に作られると一意制約違反になるため、その場合は1回だけやり直す（作成と同じ）
     *
     * @throws PostNotFoundException 投稿が存在しない場合
     * @throws PostNotFoundException ブロックの間柄にある人の投稿の場合（存在しない投稿と同じ。何も変えない）
     * @throws PostEditForbiddenException 他人の投稿の場合（何も変えない）
     * @throws InputValidationException 選択肢・タグが条件を満たさない場合（何も変えない）
     */
    public PostResponse edit(String subject, Long id, PostEditRequest request) {
        Long userId = authService.requireCurrentUser(subject).getId();
        try {
            return transactionTemplate.execute(status -> applyEdit(userId, id, request));
        } catch (DataIntegrityViolationException e) {
            log.info("投稿の編集で一意制約違反が発生したため、やり直します: {}", e.getMostSpecificCause().getMessage());
            return transactionTemplate.execute(status -> applyEdit(userId, id, request));
        }
    }

    /** トランザクションの中で呼ぶ。投稿者を確かめてから、入力を確認して書き換える */
    private PostResponse applyEdit(Long userId, Long id, PostEditRequest request) {
        Post post = postRepository.findById(id).orElseThrow(() -> new PostNotFoundException(id));
        // 先に見えるかを確かめる。ブロックの間柄にある人の投稿は、存在しない投稿と同じ 404（403 にすると投稿が残っていることが分かる。
        // docs/profile.md「ブロック」）。見えるが他人の投稿なら 403
        userVisibility.requireVisiblePost(userId, post);
        if (!post.getAuthor().getId().equals(userId)) {
            throw new PostEditForbiddenException(id);
        }

        Map<String, String> errors = new LinkedHashMap<>();
        FashionCategory fashionCategory = findSelectableFashionCategory(request.fashionCategoryId(), post.getFashionCategory());
        if (fashionCategory == null) {
            errors.put("fashionCategoryId", FASHION_CATEGORY_MESSAGE);
        }
        // この投稿に今付いているタグ（DB の値）。無効になっていても、そのまま残せる
        Set<String> currentTagKeys = post.getTags().stream().map(Tag::getNormalizedName).collect(Collectors.toSet());
        List<String> tagNames = List.of();
        try {
            tagNames = tagService.validateForPost(request.tags(), currentTagKeys);
        } catch (InputValidationException e) {
            errors.putAll(e.getErrors());
        }
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        List<Tag> tags = tagService.findOrCreateForPost(tagNames, currentTagKeys);
        post.edit(request.title(), fashionCategory, request.wornItems(), request.description(), request.referenceUrl(),
                tags, LocalDateTime.now());
        postRepository.flush();
        return PostResponse.from(post, reactionSummaryService.summarize(userId, List.of(id)).get(id), userId);
    }

    /**
     * 投稿を削除する（docs/post.md「投稿の削除」）。削除できるのは投稿者本人だけ。
     *
     * <p>次の順に進める。写真のファイルは DB のようにロールバックできないため、DB の削除が確定してから消す。
     * <ol>
     *   <li>1つのトランザクションで、投稿を読み、投稿者を確かめ、写真の URL を控えてから投稿を削除する。
     *       写真・タグとのつながりは JPA が、いいね・お気に入り・通知は DB の外部キー（ON DELETE CASCADE）が一緒に削除する</li>
     *   <li>コミットの後に、控えた URL の写真のファイルを1件ずつ削除する。
     *       対象は post_images に記録されていた URL そのものだけ（パターンやワイルドカードで探して消すことはしない）。
     *       削除に失敗しても DB は戻さない（投稿は削除済みとして扱い、残ったファイルはログに残す）</li>
     * </ol>
     *
     * @throws PostNotFoundException 投稿が存在しない場合（すでに削除された場合を含む）
     * @throws PostNotFoundException ブロックの間柄にある人の投稿の場合も同じ（何も削除しない）
     * @throws PostDeleteForbiddenException 他人の投稿の場合（何も削除しない）
     */
    public void delete(String subject, Long id) {
        Long userId = authService.requireCurrentUser(subject).getId();
        List<String> imageUrls;
        try {
            imageUrls = transactionTemplate.execute(status -> {
                Post post = postRepository.findById(id).orElseThrow(() -> new PostNotFoundException(id));
                // 編集と同じく、ブロックの間柄にある人の投稿は 404、見えるが他人の投稿なら 403
                userVisibility.requireVisiblePost(userId, post);
                if (!post.getAuthor().getId().equals(userId)) {
                    throw new PostDeleteForbiddenException(id);
                }
                List<String> urls = post.getImages().stream().map(PostImage::getImageUrl).toList();
                postRepository.delete(post);
                return urls;
            });
        } catch (ObjectOptimisticLockingFailureException e) {
            // 同じ投稿の削除がほぼ同時に届き、先に届いた方で削除済みになった
            throw new PostNotFoundException(id);
        }
        log.info("投稿を削除しました: postId={}, userId={}, images={}", id, userId, imageUrls.size());
        imageUploadService.deleteQuietly(imageUrls);
    }

    /**
     * ログイン中のユーザーの投稿一覧（新しい順）。
     * page は 0 から。範囲外の page・size はエラーにせず、0 以上・1〜MAX_PAGE_SIZE に丸める
     */
    @Transactional(readOnly = true)
    public PostListResponse listMine(String subject, int page, int size) {
        Long authorId = authService.requireCurrentUser(subject).getId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        Slice<Post> posts = postRepository.findByAuthorIdOrderByCreatedAtDescIdDesc(
                authorId, PageRequest.of(safePage, safeSize));
        // いいね・お気に入りは投稿ごとではなく、ページ分をまとめて調べる（N+1 問題を防ぐ）
        Map<Long, ReactionSummary> reactions = reactionSummaryService.summarize(
                authorId, posts.getContent().stream().map(Post::getId).toList());
        return new PostListResponse(
                posts.getContent().stream().map(post -> PostResponse.from(post, reactions.get(post.getId()), authorId)).toList(),
                safePage,
                safeSize,
                posts.hasNext());
    }

    /** 選択肢として出しているもの（有効なもの）だけを受け付ける */
    private Optional<FashionCategory> findActiveFashionCategory(Long id) {
        return fashionCategoryRepository.findById(id).filter(FashionCategory::isActive);
    }

    /**
     * 編集で選べるファッションの種類。投稿に設定済みのもの（current。DB の値）は無効になっていてもそのまま使い、
     * それ以外は有効なものだけを受け付ける。選べなければ null
     */
    private FashionCategory findSelectableFashionCategory(Long id, FashionCategory current) {
        if (current.getId().equals(id)) {
            return current;
        }
        return findActiveFashionCategory(id).orElse(null);
    }
}
