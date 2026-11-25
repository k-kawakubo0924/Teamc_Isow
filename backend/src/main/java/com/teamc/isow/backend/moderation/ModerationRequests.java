package com.teamc.isow.backend.moderation;

/** 措置の API の入力（docs/admin.md「6. 措置」） */
public final class ModerationRequests {

    private ModerationRequests() {
    }

    /**
     * 投稿を非公開にする
     *
     * @param reason 理由（ReportReason の定数名。投稿に使える理由だけ）
     * @param memo 管理者のメモ（任意。管理画面にだけ出し、ログには書かない）
     * @param reportCaseId 通報から措置する場合、その通報（同じ投稿の通報であること）
     * @param resolveReport true なら、結びつけた通報を同時に「対応済み」にする
     */
    public record Hide(String reason, String memo, Long reportCaseId, Boolean resolveReport) {
    }

    /**
     * ユーザーを利用停止にする
     *
     * @param days 期間（1・3・7・30日）。null なら無期限
     * @param reportCaseId 通報から措置する場合、その通報（そのユーザーが通報された人であること）
     */
    public record Suspend(String reason, Integer days, String memo, Long reportCaseId, Boolean resolveReport) {
    }

    /** 公開に戻す・利用停止を解除する */
    public record Undo(String memo) {
    }
}
