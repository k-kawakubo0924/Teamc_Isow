package com.teamc.isow.backend.contact;

/**
 * お問い合わせの種類（docs/settings.md「お問い合わせ」。design/Contact us.png の選択肢）。
 * DB には定数名を文字列で保存する（{@code @Enumerated} は使わない。理由は AdminAction と同じ）。定数名は変更しないこと。
 * 画面の選択肢（frontend/src/api/contacts.ts）と同じ（変えるときは両方を直すこと）
 */
public enum ContactCategory {

    APP("アプリについて"),
    USER("ユーザーについて"),
    ACCOUNT("アカウントについて"),
    OTHER("その他");

    private final String label;

    ContactCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
