package com.teamc.isow.backend.post;

import java.util.List;

/**
 * プロフィールの投稿一覧・お気に入り一覧（1ページ分）。投稿はホームの一覧と同じ形（TimelineResponse.Item）。
 *
 * @param posts 並び順どおりの投稿
 * @param page ページ番号（0 から）
 * @param size 1ページあたりの件数（上限で丸めた後の値）
 * @param hasNext 次のページがあるか
 */
public record PostCardListResponse(List<TimelineResponse.Item> posts, int page, int size, boolean hasNext) {
}
