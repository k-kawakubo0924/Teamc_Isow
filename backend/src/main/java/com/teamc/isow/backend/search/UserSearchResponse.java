package com.teamc.isow.backend.search;

import com.teamc.isow.backend.common.Gender;
import java.util.List;

/**
 * ユーザーの検索結果（1ページ分。GET /api/search/users）。
 *
 * @param users 完全一致 → 前方一致 → 部分一致 → ユーザー名の順
 * @param page ページ番号（0 から）
 * @param size 1ページあたりの件数（上限で丸めた後の値）
 * @param hasNext 次のページがあるか
 * @param totalCount 条件に一致したユーザーの件数
 */
public record UserSearchResponse(List<Item> users, int page, int size, boolean hasNext, long totalCount) {

    /**
     * 結果の1件。メールアドレスなどの個人情報は返さない（フォロー一覧と同じ項目）。
     *
     * @param displayName 表示名。未設定ならユーザー名
     * @param profileImageUrl プロフィール画像。未設定は null
     * @param heightCm 身長（cm）。未設定は null で、画面では表示を省く
     * @param gender 性別。未設定は null で、画面では表示を省く
     * @param followingByMe ログイン中のユーザーが相手をフォローしているか（ボタンの「フォロー」「フォロー解除」の切り替えに使う）
     */
    public record Item(
            Long id,
            String username,
            String displayName,
            String profileImageUrl,
            Integer heightCm,
            Gender gender,
            boolean followingByMe) {
    }
}
