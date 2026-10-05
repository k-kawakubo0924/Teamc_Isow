package com.teamc.isow.backend.follow;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserNotFoundException;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * フォロー・フォロー解除と、フォロー中一覧・フォロワー一覧（docs/profile.md）。
 *
 * <p>連打に備え、何回呼んでも結果が同じになるようにする（すでにフォロー済みでも、フォローしていなくてもエラーにしない）。
 * 応答は操作後の状態とし、画面はそれをそのまま表示に反映する。
 *
 * <p>押し間違いを戻せるように、解除から {@link #UNDO_PERIOD} 以内は次のように扱う。
 * <ul>
 *   <li>本人のフォロー中一覧に残す（ボタンは「フォロー」になる）</li>
 *   <li>再フォローした場合は、新しい行を作らず解除した行を有効に戻す。フォローした日時は元のまま</li>
 *   <li>通知は送らない（通知機能を作るときは、新しい行を作ったときだけ通知を作ること）</li>
 * </ul>
 */
@Service
public class FollowService {

    /** 解除を取り消せる期間。この期間内は一覧に残し、再フォローすると元の行に戻す */
    public static final Duration UNDO_PERIOD = Duration.ofMinutes(5);

    /** 1ページあたりの件数の上限（ホームの投稿一覧と同じ） */
    static final int MAX_PAGE_SIZE = 50;

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final TransactionTemplate transactionTemplate;

    public FollowService(
            FollowRepository followRepository,
            UserRepository userRepository,
            AuthService authService,
            PlatformTransactionManager transactionManager) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** フォローする。すでにフォロー済みでも成功として扱う。自分自身は 400、存在しないユーザーは 404 */
    public FollowResponse follow(String subject, Long followeeId) {
        Long followerId = authService.requireCurrentUser(subject).getId();
        if (followerId.equals(followeeId)) {
            throw new SelfFollowException();
        }
        try {
            transactionTemplate.executeWithoutResult(status -> followIfAbsent(followerId, followeeId));
        } catch (DataIntegrityViolationException e) {
            // 連打で同じリクエストがほぼ同時に届くと、両方が「未フォロー」と判断して片方が一意制約違反になる。
            // その場合は先に届いた方でフォロー済みのため、成功として扱う。
            // フォローされていない場合（確認の直後に相手が削除されたなど）は、本当の失敗なのでそのまま投げる
            if (!followRepository.existsActive(followerId, followeeId)) {
                throw e;
            }
        }
        return new FollowResponse(true, followRepository.countByFolloweeIdAndActiveTrue(followeeId));
    }

    /** フォローを解除する。フォローしていなくても成功として扱う。存在しないユーザーは 404 */
    public FollowResponse unfollow(String subject, Long followeeId) {
        Long followerId = authService.requireCurrentUser(subject).getId();
        transactionTemplate.executeWithoutResult(status -> {
            requireUserExists(followeeId);
            followRepository.unfollow(followerId, followeeId, LocalDateTime.now());
        });
        return new FollowResponse(false, followRepository.countByFolloweeIdAndActiveTrue(followeeId));
    }

    /**
     * userId のユーザーのフォロー中一覧。本人が自分の一覧を見る場合だけ、解除から UNDO_PERIOD 以内のものも含める
     * （押し間違いを戻すための仕様のため、他の人には見せない）
     */
    @Transactional(readOnly = true)
    public FollowListResponse listFollowings(String subject, Long userId, FollowSort sort, int page, int size) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        requireUserExists(userId);
        Pageable pageable = pageable(sort, page, size);
        Slice<Follow> follows = viewerId.equals(userId)
                ? followRepository.findFollowingsIncludingUnfollowedSince(
                        userId, LocalDateTime.now().minus(UNDO_PERIOD), pageable)
                : followRepository.findActiveFollowings(userId, pageable);
        return toResponse(viewerId, follows, Follow::getFollowee,
                followRepository.countByFollowerIdAndActiveTrue(userId), sort);
    }

    /** userId のユーザーのフォロワー一覧（有効なフォローのみ） */
    @Transactional(readOnly = true)
    public FollowListResponse listFollowers(String subject, Long userId, FollowSort sort, int page, int size) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        requireUserExists(userId);
        Slice<Follow> follows = followRepository.findActiveFollowers(userId, pageable(sort, page, size));
        return toResponse(viewerId, follows, Follow::getFollower,
                followRepository.countByFolloweeIdAndActiveTrue(userId), sort);
    }

    /** トランザクションの中で呼ぶ */
    private void followIfAbsent(Long followerId, Long followeeId) {
        requireUserExists(followeeId);
        if (followRepository.existsActive(followerId, followeeId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        followRepository
                .findFirstByFollowerIdAndFolloweeIdAndUnfollowedAtAfterOrderByUnfollowedAtDesc(
                        followerId, followeeId, now.minus(UNDO_PERIOD))
                .ifPresentOrElse(
                        recent -> followRepository.restore(recent.getId(), now),
                        () -> followRepository.save(new Follow(
                                userRepository.getReferenceById(followerId),
                                userRepository.getReferenceById(followeeId))));
    }

    /** page は 0 から。範囲外の page・size はエラーにせず、0 以上・1〜MAX_PAGE_SIZE に丸める（ホームの一覧と同じ） */
    private static Pageable pageable(FollowSort sort, int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), sort.toSort());
    }

    /** otherSide は一覧に表示する相手（フォロー中一覧ならフォローされる側、フォロワー一覧ならフォローする側） */
    private FollowListResponse toResponse(Long viewerId, Slice<Follow> follows, Function<Follow, User> otherSide,
            long totalCount, FollowSort sort) {
        List<User> users = follows.getContent().stream().map(otherSide).toList();
        Set<Long> followingByMe = users.isEmpty()
                ? Set.of()
                : new HashSet<>(followRepository.findFollowingIds(viewerId, users.stream().map(User::getId).toList()));
        List<FollowListResponse.Item> items = follows.getContent().stream()
                .map(follow -> {
                    User user = otherSide.apply(follow);
                    return new FollowListResponse.Item(
                            user.getId(),
                            user.getUsername(),
                            user.getDisplayName(),
                            user.getProfileImageUrl(),
                            user.getHeightCm(),
                            user.getGender(),
                            follow.getFollowedAt(),
                            followingByMe.contains(user.getId()));
                })
                .toList();
        Pageable pageable = follows.getPageable();
        return new FollowListResponse(items, totalCount, sort.getParam(),
                pageable.getPageNumber(), pageable.getPageSize(), follows.hasNext());
    }

    private void requireUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
    }
}
