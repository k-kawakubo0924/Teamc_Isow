package com.teamc.isow.backend.dm;

import java.util.List;

/**
 * 複数の相手について、相談を申し込めるか（GET /api/users/consultation-statuses）。フォロー中一覧の「相談する」ボタンに使う。
 *
 * @param statuses 相手ごとの結果（ユーザーID の小さい順）。存在しないユーザーの ID は含めない
 */
public record ConsultationStatusesResponse(List<Item> statuses) {

    /**
     * @param userId 相手のユーザーID
     * @param status 申し込めるか（GET /api/users/{id}/consultation-status と同じ形）
     */
    public record Item(Long userId, ConsultationStatusResponse status) {
    }
}
