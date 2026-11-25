package com.teamc.isow.backend.search;

import com.teamc.isow.backend.post.TimelineResponse;
import java.util.List;

/**
 * 投稿の検索結果（1ページ分。GET /api/search/posts）。投稿はホームの一覧と同じ形（TimelineResponse.Item）。
 *
 * @param posts いいね数の多い順 → 新しい順 → ID の大きい順
 * @param page ページ番号（0 から）
 * @param size 1ページあたりの件数（上限で丸めた後の値）
 * @param hasNext 次のページがあるか
 * @param totalCount 条件に一致した投稿の件数（design/Searchresults.png の「検索結果 〇件」）
 */
public record PostSearchResponse(
        List<TimelineResponse.Item> posts, int page, int size, boolean hasNext, long totalCount) {
}
