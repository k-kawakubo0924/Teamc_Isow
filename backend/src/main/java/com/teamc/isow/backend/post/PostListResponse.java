package com.teamc.isow.backend.post;

import java.util.List;

/**
 * 投稿一覧（1ページ分）。
 *
 * @param posts 投稿（新しい順）
 * @param page ページ番号（0 から）
 * @param size 1ページあたりの件数（上限で丸めた後の値）
 * @param hasNext 次のページがあるか
 */
public record PostListResponse(List<PostResponse> posts, int page, int size, boolean hasNext) {
}
