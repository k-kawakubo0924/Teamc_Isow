package com.teamc.isow.backend.follow;

import com.teamc.isow.backend.common.Gender;
import java.time.LocalDateTime;
import java.util.List;

/**
 * フォロー中一覧・フォロワー一覧（1ページ分）。
 *
 * @param users 並び順どおりのユーザー
 * @param totalCount フォロー数・フォロワー数（有効なフォローのみ。画面上部に表示する）
 * @param sort 並び順（newest / oldest）
 * @param page ページ番号（0 から）
 * @param size 1ページあたりの件数（上限で丸めた後の値）
 * @param hasNext 次のページがあるか
 */
public record FollowListResponse(
        List<Item> users, long totalCount, String sort, int page, int size, boolean hasNext) {

    /**
     * 一覧の1件（design/Following List.png）。メールアドレスなどの個人情報は返さない。
     *
     * @param id 相手のユーザーID
     * @param heightCm 身長（cm）。未設定は null で、画面では表示を省く
     * @param gender 性別。未設定は null で、画面では表示を省く
     * @param followedAt フォローした日時。「2時間前」などの表示は画面側で作る
     * @param followingByMe ログイン中のユーザーが相手をフォローしているか（ボタンの「フォロー」「フォロー解除」の切り替えに使う）。
     *     自分のフォロー中一覧で、解除から5分以内のものは false になる
     */
    public record Item(
            Long id,
            String username,
            String displayName,
            String profileImageUrl,
            Integer heightCm,
            Gender gender,
            LocalDateTime followedAt,
            boolean followingByMe) {
    }
}
