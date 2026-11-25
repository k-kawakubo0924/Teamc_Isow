package com.teamc.isow.backend.profile;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.block.BlockRepository;
import com.teamc.isow.backend.block.UserVisibility;
import com.teamc.isow.backend.follow.FollowRepository;
import com.teamc.isow.backend.image.ImageUrls;
import com.teamc.isow.backend.post.PostCardListResponse;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.reaction.PostFavoriteRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserNotFoundException;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * プロフィールと、プロフィールの投稿一覧・お気に入り一覧（docs/profile.md）。
 *
 * <p>一覧は並び順どおりに投稿 ID だけを取得し、中身は PostCardService でまとめて読む。
 * 件数に関係なく SQL の本数は一定（N+1 問題を防ぐ。ProfileQueryCountTest で確認している）。
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final PostCardService postCardService;
    private final AuthService authService;
    private final Clock clock;
    private final BlockRepository blockRepository;
    private final UserVisibility userVisibility;
    private final ImageUrls imageUrls;

    public ProfileService(
            UserRepository userRepository,
            FollowRepository followRepository,
            PostRepository postRepository,
            PostFavoriteRepository favoriteRepository,
            PostCardService postCardService,
            AuthService authService,
            BlockRepository blockRepository,
            UserVisibility userVisibility,
            Clock clock,
            ImageUrls imageUrls) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.favoriteRepository = favoriteRepository;
        this.postCardService = postCardService;
        this.authService = authService;
        this.clock = clock;
        this.blockRepository = blockRepository;
        this.userVisibility = userVisibility;
        this.imageUrls = imageUrls;
    }

    /** ログイン中のユーザー自身のプロフィール */
    @Transactional(readOnly = true)
    public ProfileResponse getMine(String subject) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        return getProfile(viewerId, viewerId);
    }

    /** 指定したユーザーのプロフィール。自分の ID を指定した場合は getMine と同じ。存在しないユーザーは 404 */
    @Transactional(readOnly = true)
    public ProfileResponse get(String subject, Long userId) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        return getProfile(viewerId, userId);
    }

    /**
     * 指定したユーザーの投稿一覧（新しい順）。存在しないユーザー・見えないユーザー（ブロック・利用停止）は 404。
     * 見ている人から見えない投稿（非公開）は除く。本人が見る場合は非公開の投稿も含む
     */
    @Transactional(readOnly = true)
    public PostCardListResponse listPosts(String subject, Long userId, int page, int size) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        requireVisibleUser(viewerId, userId);
        Pageable pageable = PostCardService.pageRequest(page, size);
        return toResponse(viewerId,
                postRepository.findIdsByAuthorId(userId, viewerId, LocalDateTime.now(clock), pageable), pageable);
    }

    /**
     * 指定したユーザーがフォローしている人の投稿を、ランダムな順で返す（相手のプロフィールの「フォロー中の投稿」）。
     * seed は画面を開いたときに決めた値で、同じ seed なら何ページ目でも同じ並び順になる。
     * 存在しないユーザー・ブロックの間柄にあるユーザーは 404。見ている人とブロックの間柄にある人の投稿は除く
     */
    @Transactional(readOnly = true)
    public PostCardListResponse listFollowingPosts(String subject, Long userId, long seed, int page, int size) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        requireVisibleUser(viewerId, userId);
        Pageable pageable = PostCardService.pageRequest(page, size);
        return toResponse(viewerId,
                postRepository.findIdsByFollowedAuthorsShuffled(userId, viewerId, LocalDateTime.now(clock), seed, pageable),
                pageable);
    }

    /** ログイン中のユーザーのお気に入り一覧（お気に入りにした新しい順）。お気に入りは本人にだけ見せる */
    @Transactional(readOnly = true)
    public PostCardListResponse listMyFavorites(String subject, int page, int size) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        Pageable pageable = PostCardService.pageRequest(page, size);
        return toResponse(viewerId,
                favoriteRepository.findPostIdsByUserId(viewerId, LocalDateTime.now(clock), pageable), pageable);
    }

    /**
     * プロフィール。ブロックの間柄にある場合（docs/profile.md「ブロック」）は次のとおり。
     * <ul>
     *   <li>見ている人がブロックしている：blockedByMe を true にして返す（画面は「ブロック中のユーザーです」と解除のボタンを出す）</li>
     *   <li>見ている人がブロックされている（自分はブロックしていない）：存在しないユーザーと同じ 404</li>
     * </ul>
     * 利用停止中のユーザーは、ブロックしていても、ほかの人からは存在しないユーザーと同じ 404（docs/admin.md「6. 措置」）。
     * 投稿数・フォロー中・フォロワーの件数は、見ている人から見えないもの（非公開の投稿・ブロック・利用停止）を数えない（一覧と合わせるため）
     */
    private ProfileResponse getProfile(Long viewerId, Long userId) {
        User user = userRepository.findWithProfileById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        LocalDateTime now = LocalDateTime.now(clock);
        boolean me = viewerId.equals(userId);
        if (!me && user.isSuspendedAt(now)) {
            throw new UserNotFoundException(userId);
        }
        boolean blockedByMe = !me && blockRepository.existsByBlockerIdAndBlockedId(viewerId, userId);
        if (!blockedByMe) {
            userVisibility.requireVisibleUser(viewerId, userId);
        }
        return ProfileResponse.of(
                user,
                postRepository.countVisibleByAuthorId(userId, viewerId, now),
                followRepository.countVisibleFollowings(userId, viewerId, now),
                followRepository.countVisibleFollowers(userId, viewerId, now),
                me ? null : followRepository.existsActive(viewerId, userId),
                blockedByMe,
                imageUrls);
    }

    /** 存在して、viewerId から見えるユーザーであること。どちらでもなければ同じ UserNotFoundException（404） */
    private void requireVisibleUser(Long viewerId, Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        userVisibility.requireVisibleUser(viewerId, userId);
    }

    private PostCardListResponse toResponse(Long viewerId, Slice<Long> postIds, Pageable pageable) {
        return new PostCardListResponse(postCardService.build(viewerId, postIds.getContent()),
                pageable.getPageNumber(), pageable.getPageSize(), postIds.hasNext());
    }
}
