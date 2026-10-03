package com.teamc.isow.backend.post;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.post.PostResponse.FashionCategoryResponse;
import com.teamc.isow.backend.reaction.ReactionSummary;
import com.teamc.isow.backend.reaction.ReactionSummaryService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ホームの投稿一覧（docs/home.md）。
 *
 * <p>件数に関係なく決まった本数の SQL で取得する（投稿ごとに SQL を発行する N+1 問題を防ぐ）。
 * <ol>
 *   <li>並び順どおりに、そのページの投稿 ID だけを取得する</li>
 *   <li>その ID の投稿を、投稿者・ファッションの種類と一緒に取得する</li>
 *   <li>1枚目の写真だけをまとめて取得する</li>
 *   <li>いいね件数・自分のいいね・自分のお気に入りをまとめて調べる（ReactionSummaryService）</li>
 * </ol>
 */
@Service
public class TimelineService {

    private final PostRepository postRepository;
    private final ReactionSummaryService reactionSummaryService;
    private final AuthService authService;

    public TimelineService(
            PostRepository postRepository, ReactionSummaryService reactionSummaryService, AuthService authService) {
        this.postRepository = postRepository;
        this.reactionSummaryService = reactionSummaryService;
        this.authService = authService;
    }

    /** page は 0 から。範囲外の page・size はエラーにせず、0 以上・1〜MAX_PAGE_SIZE に丸める（自分の投稿一覧と同じ） */
    @Transactional(readOnly = true)
    public TimelineResponse list(String subject, TimelineTab tab, int page, int size) {
        Long userId = authService.requireCurrentUser(subject).getId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, PostService.MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Slice<Long> ids = switch (tab) {
            case RECOMMENDED -> postRepository.findRecommendedIds(pageable);
            case LATEST -> postRepository.findLatestIds(pageable);
        };
        List<Long> postIds = ids.getContent();
        if (postIds.isEmpty()) {
            return new TimelineResponse(List.of(), tab.getParam(), safePage, safeSize, false);
        }

        Map<Long, Post> posts = postRepository.findWithAuthorAndFashionCategoryByIdIn(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        Map<Long, String> thumbnails = postRepository.findThumbnails(postIds).stream()
                .collect(Collectors.toMap(PostThumbnail::postId, PostThumbnail::imageUrl));
        Map<Long, ReactionSummary> reactions = reactionSummaryService.summarize(userId, postIds);

        // 1. で取得した並び順どおりに組み立てる（2. の結果は順不同のため）。
        // 1. と 2. の間に削除された投稿は飛ばす
        List<TimelineResponse.Item> items = postIds.stream()
                .map(posts::get)
                .filter(Objects::nonNull)
                .map(post -> toItem(post, thumbnails.get(post.getId()), reactions.get(post.getId())))
                .toList();
        return new TimelineResponse(items, tab.getParam(), safePage, safeSize, ids.hasNext());
    }

    private static TimelineResponse.Item toItem(Post post, String thumbnailUrl, ReactionSummary reactions) {
        return new TimelineResponse.Item(
                post.getId(),
                thumbnailUrl,
                new FashionCategoryResponse(post.getFashionCategory().getId(), post.getFashionCategory().getName()),
                new TimelineResponse.Author(
                        post.getAuthor().getId(), post.getAuthor().getUsername(), post.getAuthor().getHeightCm()),
                reactions.likeCount(),
                reactions.likedByMe(),
                reactions.favoritedByMe(),
                post.getCreatedAt());
    }
}
