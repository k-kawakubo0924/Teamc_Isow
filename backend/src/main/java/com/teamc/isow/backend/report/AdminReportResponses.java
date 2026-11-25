package com.teamc.isow.backend.report;

import java.time.LocalDateTime;
import java.util.List;

/** 管理画面の通報の応答（/api/admin/reports/**。docs/admin.md「通報」） */
public final class AdminReportResponses {

    private AdminReportResponses() {
    }

    /** 一覧（対象ごとに1行。新しく通報された順） */
    public record ListResponse(List<ListItem> items, int page, int size, boolean hasNext) {
    }

    /**
     * 一覧の1行。DM の本文は出さない（見たことを記録する詳細でだけ出す）
     *
     * @param summary 投稿の題名・「@ユーザー名」・「@ユーザー名 のメッセージ」。証拠を消した後は null
     * @param reasons 理由ごとの件数（多い順）
     */
    public record ListItem(Long caseId, ReportTargetType targetType, Long targetId, ReportStatus status, String summary,
            int reportCount, List<ReasonCount> reasons, LocalDateTime firstReportedAt, LocalDateTime lastReportedAt,
            boolean evidencePurged) {
    }

    public record ReasonCount(ReportReason reason, long count) {
    }

    /**
     * 詳細
     *
     * @param targetUserId 通報された人（投稿者・ユーザー本人・メッセージを送った人）。措置で利用停止にする相手
     * @param evidence 通報した時点の内容のコピー。保存期間を過ぎて消した後は null
     * @param evidenceFiles 証拠の写真（中身は evidence-files/{id} から管理者だけが取得できる）
     * @param currentState 対象の今の状態（EXISTS・EDITED（通報の後に編集された）・DELETED）
     * @param reports 通報した人と理由（古い順）。通報した人は管理者にだけ見せる
     */
    public record Detail(Long caseId, ReportTargetType targetType, Long targetId, Long targetUserId, ReportStatus status,
            int reportCount, LocalDateTime firstReportedAt, LocalDateTime lastReportedAt, LocalDateTime closedAt,
            String handledByUsername, String adminMemo, boolean evidencePurged, LocalDateTime evidencePurgedAt,
            EvidenceSnapshot evidence, List<EvidenceFile> evidenceFiles, CurrentState currentState,
            List<ReportItem> reports) {
    }

    /** 証拠の写真1枚。messageId は DM の画像のとき、そのメッセージの ID */
    public record EvidenceFile(Long id, Long messageId) {
    }

    public record ReportItem(Long reporterId, String reporterUsername, ReportReason reason, String comment,
            LocalDateTime reportedAt) {
    }

    /** 状態を変えた結果（証拠は返さない。DM の中身を見るのは詳細だけにするため） */
    public record StatusResult(Long caseId, ReportStatus status, LocalDateTime closedAt, String handledByUsername,
            String adminMemo) {
    }

    public enum CurrentState {
        /** 今もある（通報の後に変わっていない） */
        EXISTS,
        /** 通報の後に投稿者が編集した（証拠は通報した時点のまま） */
        EDITED,
        /** 削除された（証拠だけが残っている） */
        DELETED
    }
}
