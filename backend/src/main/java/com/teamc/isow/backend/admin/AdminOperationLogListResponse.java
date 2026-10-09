package com.teamc.isow.backend.admin;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理操作のログの一覧（1ページ分。GET /api/admin/operation-logs）。
 *
 * @param logs 新しい順
 * @param page ページ番号（0 から）
 * @param size 1ページあたりの件数（1〜50 に丸めた後の値）
 * @param hasNext 次のページがあるか
 */
public record AdminOperationLogListResponse(List<Item> logs, int page, int size, boolean hasNext) {

    /**
     * ログの1件。
     *
     * @param operatorId 操作した管理者のユーザー ID。システムの操作なら null
     * @param operatorUsername 操作した時点のユーザー名。システムの操作なら「システム」
     * @param system システムの操作か
     * @param action 操作の種類（AdminAction の定数名）
     * @param targetType 対象の種類（AdminTargetType の定数名）
     */
    public record Item(
            Long id,
            LocalDateTime operatedAt,
            Long operatorId,
            String operatorUsername,
            boolean system,
            String action,
            String targetType,
            Long targetId,
            String detail) {

        static Item from(AdminOperationLog log) {
            return new Item(log.getId(), log.getOperatedAt(), log.getOperatorId(), log.getOperatorUsername(),
                    log.isSystem(), log.getAction(), log.getTargetType(), log.getTargetId(), log.getDetail());
        }
    }
}
