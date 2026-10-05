package com.teamc.isow.backend.common;

/**
 * 性別。プロフィール（docs/profile.md）と検索条件（docs/search.md）で共通して使う。
 *
 * <p>DB には定数名を文字列の列として保存する（User.gender などを参照）。
 * {@code @Enumerated} は使わない。Hibernate が列に値の一覧の検査制約を付け、ddl-auto=update では定数を追加しても
 * 制約が更新されず、新しい値を保存できなくなるため（コンバーターを使っても同じ制約が付く）。
 * 順番の数値（ORDINAL）も、並び替え・追加で既存データの意味が変わるため使わない。
 * 定数名は既存データとの対応に使うため変更しないこと。画面の文言を変える場合は label だけを変える。
 * 未設定・指定なしは null で表し、定数としては持たない。
 */
public enum Gender {

    /** 男性 */
    MALE("男性"),
    /** 女性 */
    FEMALE("女性"),
    /** その他 */
    OTHER("その他");

    /** 画面に表示する文言。DB には保存しないため、変更しても既存データに影響しない */
    private final String label;

    Gender(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
