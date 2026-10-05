package com.teamc.isow.backend.dm;

/**
 * 会話の状態（docs/dm.md「会話の状態」）。
 *
 * <p>DB には定数名を文字列の列として保存する（Conversation.status を参照）。
 * {@code @Enumerated} は使わない。Hibernate が列に値の一覧の検査制約を付け、ddl-auto=update では定数を追加しても
 * 制約が更新されず、新しい値を保存できなくなるため（コンバーターを使っても同じ制約が付く）。
 * 順番の数値（ORDINAL）も、並び替え・追加で既存データの意味が変わるため使わない。
 * 定数名は既存データとの対応に使うため変更しないこと。画面の文言を変える場合は label だけを変える。
 */
public enum ConversationStatus {

    /** 申請中。相手の承認待ち（相談を申し込んだとき） */
    REQUESTED("申請中", true),
    /** 進行中。会話できる（申請中の会話を相手が承認したとき） */
    ACTIVE("進行中", true),
    /** 終了。「会話終了」または一か月連絡がなかったとき */
    ENDED("終了", false),
    /** 拒否。申請中の会話を相手が拒否したとき。履歴として残す */
    REJECTED("拒否", false);

    /** 画面に表示する文言。DB には保存しないため、変更しても既存データに影響しない */
    private final String label;

    /** 同じ2人の間で1つまでに数える状態か（申請中・進行中）。Conversation.ongoing の値を決める */
    private final boolean ongoing;

    ConversationStatus(String label, boolean ongoing) {
        this.label = label;
        this.ongoing = ongoing;
    }

    public String getLabel() {
        return label;
    }

    public boolean isOngoing() {
        return ongoing;
    }
}
