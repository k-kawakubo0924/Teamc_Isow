package com.teamc.isow.backend.profile;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.follow.FollowRepository;
import com.teamc.isow.backend.post.PostCardListResponse;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.reaction.PostFavoriteRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserNotFoundException;
import com.teamc.isow.backend.user.UserRepository;
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

    public ProfileService(
            UserRepository userRepository,
            FollowRepository followRepository,
            PostRepository postRepository,
            PostFavoriteRepository favoriteRepository,
            PostCardService postCardService,
            AuthService authService) {
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.postRepository = postRepository;
        this.favoriteRepository = favoriteRepository;
        this.postCardService = postCardService;
        this.authService = authService;
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

    /** 指定したユーザーの投稿一覧（新しい順）。存在しないユーザーは 404 */
    @Transactional(readOnly = true)
    public PostCardListResponse listPosts(String subject, Long userId, int page, int size) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        Pageable pageable = PostCardService.pageRequest(page, size);
        return toResponse(viewerId, postRepository.findIdsByAuthorId(userId, pageable), pageable);
    }

    /** ログイン中のユーザーのお気に入り一覧（お気に入りにした新しい順）。お気に入りは本人にだけ見せる */
    @Transactional(readOnly = true)
    public PostCardListResponse listMyFavorites(String subject, int page, int size) {
        Long viewerId = authService.requireCurrentUser(subject).getId();
        Pageable pageable = PostCardService.pageRequest(page, size);
        return toResponse(viewerId, favoriteRepository.findPostIdsByUserId(viewerId, pageable), pageable);
    }

    private ProfileResponse getProfile(Long viewerId, Long userId) {
        User user = userRepository.findWithProfileById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        boolean me = viewerId.equals(userId);
        return ProfileResponse.of(
                user,
                followRepository.countByFollowerIdAndActiveTrue(userId),
                followRepository.countByFolloweeIdAndActiveTrue(userId),
                me ? null : followRepository.existsActive(viewerId, userId));
    }

    private PostCardListResponse toResponse(Long viewerId, Slice<Long> postIds, Pageable pageable) {
        return new PostCardListResponse(postCardService.build(viewerId, postIds.getContent()),
                pageable.getPageNumber(), pageable.getPageSize(), postIds.hasNext());
    }
}
