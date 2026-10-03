package com.teamc.isow.backend.reaction;

/**
 * お気に入りの操作後の状態。お気に入りは本人だけのものなので、件数は返さない。
 *
 * @param favorited ログイン中のユーザーがお気に入りにしているか
 */
public record FavoriteResponse(boolean favorited) {
}
