package com.teamc.isow.backend.post;

import com.teamc.isow.backend.auth.AuthService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ホームの投稿一覧（docs/home.md）。
 *
 * <p>件数に関係なく決まった本数の SQL で取得する（投稿ごとに SQL を発行する N+1 問題を防ぐ）。
 * 並び順どおりに、そのページの投稿 ID だけを取得し、中身は PostCardService でまとめて読む。
 */
@Service
public class TimelineService {

    private final PostRepository postRepository;
    private final PostCardService postCardService;
    private final AuthService authService;

    public TimelineService(PostRepository postRepository, PostCardService postCardService, AuthService authService) {
        this.postRepository = postRepository;
        this.postCardService = postCardService;
        this.authService = authService;
    }

    /** page は 0 から。範囲外の page・size はエラーにせず、0 以上・1〜MAX_PAGE_SIZE に丸める（自分の投稿一覧と同じ） */
    @Transactional(readOnly = true)
    public TimelineResponse list(String subject, TimelineTab tab, int page, int size) {
        Long userId = authService.requireCurrentUser(subject).getId();
        Pageable pageable = PostCardService.pageRequest(page, size);

        Slice<Long> ids = switch (tab) {
            case RECOMMENDED -> postRepository.findRecommendedIds(pageable);
            case FOLLOWING -> postRepository.findFollowingIds(userId, pageable);
            case LATEST -> postRepository.findLatestIds(pageable);
        };
        return new TimelineResponse(postCardService.build(userId, ids.getContent()), tab.getParam(),
                pageable.getPageNumber(), pageable.getPageSize(), ids.hasNext());
    }
}
