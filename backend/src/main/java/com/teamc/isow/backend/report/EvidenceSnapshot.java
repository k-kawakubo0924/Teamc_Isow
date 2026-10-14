package com.teamc.isow.backend.report;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通報した時点の内容のコピー（docs/admin.md「通報」の「証拠」）。ReportCase.evidence_json に JSON で保存し、管理者の詳細でそのまま返す。
 * 対象の種類に応じて、post・user・messages のどれか1つだけが入る（ほかは null）。写真は ReportEvidenceFile に別に持つ
 */
public record EvidenceSnapshot(PostEvidence post, UserEvidence user, List<MessageEvidence> messages) {

    /** 投稿の内容 */
    public record PostEvidence(Long postId, Long authorId, String authorUsername, String title, String fashionCategory,
            List<String> tags, String wornItems, String description, String referenceUrl, LocalDateTime createdAt,
            LocalDateTime editedAt) {
    }

    /** ユーザーの名前（プロフィール画像は ReportEvidenceFile） */
    public record UserEvidence(Long userId, String username, String displayName) {
    }

    /**
     * DM のメッセージ1件（通報したメッセージと、その前後5件ずつ）
     *
     * @param reported 通報されたメッセージか（11件のうちどれかを画面で示すため）
     * @param hasImage 画像が付いていたか（画像は ReportEvidenceFile の messageId で結びつける）
     */
    public record MessageEvidence(Long messageId, Long senderId, String senderUsername, String body, boolean hasImage,
            LocalDateTime sentAt, boolean reported) {
    }
}
