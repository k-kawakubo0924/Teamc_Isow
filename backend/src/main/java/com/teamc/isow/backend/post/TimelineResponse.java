package com.teamc.isow.backend.post;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ホームの投稿一覧（1ページ分。GET /api/posts）。
 *
 * @param posts タブの並び順どおりの投稿
 * @param tab 表示したタブ（recommended / following / latest）
 * @param page ページ番号（0 から）
 * @param size 1ページあたりの件数（上限で丸めた後の値）
 * @param hasNext 次のページがあるか
 */
public record TimelineResponse(List<Item> posts, String tab, int page, int size, boolean hasNext) {

    /**
     * 一覧の1件（design/home.png のカード）。
     *
     * @param thumbnailUrl 1枚目の写真
     * @param likeCount いいね件数（公開情報）
     * @param likedByMe ログイン中のユーザーがいいねしているか
     * @param favoritedByMe ログイン中のユーザーがお気に入りにしているか（本人にだけ返す）
     */
    public record Item(
            Long id,
            String thumbnailUrl,
            PostResponse.FashionCategoryResponse fashionCategory,
            Author author,
            long likeCount,
            boolean likedByMe,
            boolean favoritedByMe,
            LocalDateTime createdAt) {
    }

    /**
     * 投稿者。メールアドレスなどの個人情報は返さない。
     *
     * @param heightCm 身長（cm）。未設定は null で、画面では表示を省く
     */
    public record Author(Long id, String username, Integer heightCm) {
    }
}
