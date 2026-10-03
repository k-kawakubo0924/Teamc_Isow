package com.teamc.isow.backend.reaction;

/**
 * いいねの操作後の状態。
 *
 * @param liked ログイン中のユーザーがいいねしているか
 * @param likeCount 投稿のいいね件数（公開情報）
 */
public record LikeResponse(boolean liked, long likeCount) {
}
