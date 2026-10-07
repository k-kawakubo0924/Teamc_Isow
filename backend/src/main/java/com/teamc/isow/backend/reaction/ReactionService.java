package com.teamc.isow.backend.reaction;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.notification.NotificationEvents;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostNotFoundException;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.function.BooleanSupplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * いいね・お気に入りの登録と取り消し（docs/home.md）。
 *
 * <p>連打に備え、何回呼んでも結果が同じになるようにする（すでにいいね済みでも、いいねしていなくてもエラーにしない）。
 * 応答は操作後の状態とし、画面はそれをそのまま表示に反映する。
 */
@Service
public class ReactionService {

    private final PostLikeRepository likeRepository;
    private final PostFavoriteRepository favoriteRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactionTemplate;

    public ReactionService(
            PostLikeRepository likeRepository,
            PostFavoriteRepository favoriteRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            AuthService authService,
            ApplicationEventPublisher events,
            PlatformTransactionManager transactionManager) {
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.events = events;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * いいねする。すでにいいね済みでも成功として扱う。
     * 新しく登録したときだけ、投稿者への通知のきっかけを出す（通知はコミット後に NotificationService が作る）
     */
    public LikeResponse like(String subject, Long postId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        addIfAbsent(
                () -> likeRepository.existsByUserIdAndPostId(userId, postId),
                () -> {
                    likeRepository.save(new PostLike(userReference(userId), findPost(postId)));
                    events.publishEvent(new NotificationEvents.Liked(postId, userId));
                });
        return new LikeResponse(true, likeRepository.countByPostId(postId));
    }

    /** いいねを取り消す。いいねしていなくても成功として扱う */
    public LikeResponse unlike(String subject, Long postId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        transactionTemplate.executeWithoutResult(status -> {
            findPost(postId);
            likeRepository.deleteByUserIdAndPostId(userId, postId);
        });
        return new LikeResponse(false, likeRepository.countByPostId(postId));
    }

    /** お気に入りに追加する。すでに追加済みでも成功として扱う */
    public FavoriteResponse favorite(String subject, Long postId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        addIfAbsent(
                () -> favoriteRepository.existsByUserIdAndPostId(userId, postId),
                () -> favoriteRepository.save(new PostFavorite(userReference(userId), findPost(postId))));
        return new FavoriteResponse(true);
    }

    /** お気に入りから外す。お気に入りにしていなくても成功として扱う */
    public FavoriteResponse unfavorite(String subject, Long postId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        transactionTemplate.executeWithoutResult(status -> {
            findPost(postId);
            favoriteRepository.deleteByUserIdAndPostId(userId, postId);
        });
        return new FavoriteResponse(false);
    }

    /**
     * まだ登録されていなければ登録する。
     * 連打で同じリクエストがほぼ同時に届くと、両方が「未登録」と判断して片方が一意制約違反になる。
     * その場合は先に届いた方で登録済みのため、結果として成功として扱う。
     * ただし登録されていない場合（確認の直後に投稿が削除されたなど）は、本当の失敗なのでそのまま投げる
     */
    private void addIfAbsent(BooleanSupplier exists, Runnable insert) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (!exists.getAsBoolean()) {
                    insert.run();
                }
            });
        } catch (DataIntegrityViolationException e) {
            if (!exists.getAsBoolean()) {
                throw e;
            }
        }
    }

    /** トランザクションの中で呼ぶ。存在しない投稿は 404 */
    private Post findPost(Long postId) {
        return postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException(postId));
    }

    /** ユーザーの行を読み込まずに、外部キーとして使う参照を作る（トランザクションの中で呼ぶ） */
    private User userReference(Long userId) {
        return userRepository.getReferenceById(userId);
    }
}
