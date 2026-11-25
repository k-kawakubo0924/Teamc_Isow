package com.teamc.isow.backend.report;

/**
 * 通報の対象の種類（docs/admin.md「通報」）。DB には定数名を文字列で保存する（種類を足しても保存できなくならないように）。
 * 定数名は既存のデータとの対応に使うため変えないこと
 */
public enum ReportTargetType {

    /** 投稿 */
    POST,
    /** ユーザー */
    USER,
    /** DM のメッセージ */
    MESSAGE
}
