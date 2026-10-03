package com.teamc.isow.backend.reaction;

/** 投稿ごとのいいね件数の集計結果（PostLikeRepository.countByPostIds の1行） */
public record PostLikeCount(Long postId, long count) {
}
