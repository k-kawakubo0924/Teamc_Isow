package com.teamc.isow.backend.follow;

/**
 * フォロー・解除の操作後の状態。
 *
 * @param following ログイン中のユーザーが相手をフォローしているか
 * @param followerCount 相手のフォロワー数（有効なフォローのみ）
 */
public record FollowResponse(boolean following, long followerCount) {
}
