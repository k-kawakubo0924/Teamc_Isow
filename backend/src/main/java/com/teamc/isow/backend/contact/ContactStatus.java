package com.teamc.isow.backend.contact;

/** お問い合わせの状態（docs/admin.md「7. お問い合わせ」）。DB には定数名を文字列で保存する */
public enum ContactStatus {

    /** 未対応 */
    OPEN("未対応"),
    /** 対応済み */
    RESOLVED("対応済み"),
    /** 対応不要（いたずら・重複など） */
    DISMISSED("対応不要");

    private final String label;

    ContactStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 対応を終えた状態か */
    public boolean isClosed() {
        return this != OPEN;
    }
}
