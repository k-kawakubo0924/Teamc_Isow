package com.teamc.isow.backend.reaction;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 投稿の応答に含める、いいね・お気に入りの状態をまとめて調べる。
 * 投稿の件数に関係なく SQL は3本（いいね件数・自分のいいね・自分のお気に入り）で済むようにし、
 * 一覧で投稿ごとに SQL が発行される（N+1 問題）のを防ぐ。
 */
@Service
public class ReactionSummaryService {

    private final PostLikeRepository likeRepository;
    private final PostFavoriteRepository favoriteRepository;

    public ReactionSummaryService(PostLikeRepository likeRepository, PostFavoriteRepository favoriteRepository) {
        this.likeRepository = likeRepository;
        this.favoriteRepository = favoriteRepository;
    }

    /**
     * 投稿 ID → いいね・お気に入りの状態。渡したすべての ID について値を返す（何もなければ NONE）。
     *
     * @param userId ログイン中のユーザー（likedByMe・favoritedByMe の判定に使う）
     */
    @Transactional(readOnly = true)
    public Map<Long, ReactionSummary> summarize(Long userId, Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> likeCounts = likeRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(PostLikeCount::postId, PostLikeCount::count));
        Set<Long> liked = new HashSet<>(likeRepository.findLikedPostIds(userId, postIds));
        Set<Long> favorited = new HashSet<>(favoriteRepository.findFavoritedPostIds(userId, postIds));

        Map<Long, ReactionSummary> result = new LinkedHashMap<>();
        for (Long postId : postIds) {
            result.put(postId, new ReactionSummary(
                    likeCounts.getOrDefault(postId, 0L), liked.contains(postId), favorited.contains(postId)));
        }
        return result;
    }
}
