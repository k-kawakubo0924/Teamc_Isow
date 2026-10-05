package com.teamc.isow.backend.post;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投稿一覧のカード（design/home.png。ホームの一覧・プロフィールの投稿一覧・お気に入り一覧で共通）を組み立てる。
 *
 * <p>並び順どおりの投稿 ID を受け取り、件数に関係なく決まった本数（5本）の SQL で中身を読む
 * （投稿ごとに SQL を発行する N+1 問題を防ぐ）。
 * <ol>
 *   <li>投稿を、投稿者・ファッションの種類と一緒に取得する</li>
 *   <li>1枚目の写真だけをまとめて取得する</li>
 *   <li>いいね件数・自分のいいね・自分のお気に入りをまとめて調べる（ReactionSummaryService。3本）</li>
 * </ol>
 */
@Service
public class PostCardService {

    private final PostRepository postRepository;
    private final ReactionSummaryService reactionSummaryService;

    public PostCardService(PostRepository postRepository, ReactionSummaryService reactionSummaryService) {
        this.postRepository = postRepository;
        this.reactionSummaryService = reactionSummaryService;
    }

    /** page は 0 から。範囲外の page・size はエラーにせず、0 以上・1〜MAX_PAGE_SIZE に丸める（自分の投稿一覧と同じ） */
    public static Pageable pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, PostService.MAX_PAGE_SIZE));
    }

    /**
     * postIds の並び順どおりにカードを返す。ID を取得した後に削除された投稿は飛ばす。
     *
     * @param viewerId ログイン中のユーザー（likedByMe・favoritedByMe の判定に使う）
     */
    @Transactional(readOnly = true)
    public List<TimelineResponse.Item> build(Long viewerId, List<Long> postIds) {
        if (postIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> posts = postRepository.findWithAuthorAndFashionCategoryByIdIn(postIds).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        Map<Long, String> thumbnails = postRepository.findThumbnails(postIds).stream()
                .collect(Collectors.toMap(PostThumbnail::postId, PostThumbnail::imageUrl));
        Map<Long, ReactionSummary> reactions = reactionSummaryService.summarize(viewerId, postIds);

        // 受け取った並び順どおりに組み立てる（1. の結果は順不同のため）
        return postIds.stream()
                .map(posts::get)
                .filter(Objects::nonNull)
                .map(post -> toItem(post, thumbnails.get(post.getId()), reactions.get(post.getId())))
                .toList();
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
