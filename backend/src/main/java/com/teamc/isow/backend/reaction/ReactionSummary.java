package com.teamc.isow.backend.reaction;

/**
 * 投稿1件についての、いいね・お気に入りの状態（投稿の応答に含める）。
 *
 * @param likeCount いいね件数（公開情報）
 * @param likedByMe ログイン中のユーザーがいいねしているか
 * @param favoritedByMe ログイン中のユーザーがお気に入りにしているか（本人にだけ返す）
 */
public record ReactionSummary(long likeCount, boolean likedByMe, boolean favoritedByMe) {

    /** いいね・お気に入りが1つもない状態（作成した直後の投稿など） */
    public static final ReactionSummary NONE = new ReactionSummary(0, false, false);
}
