package com.teamc.isow.backend.contact;

/**
 * お問い合わせの受付の経路（docs/settings.md「お問い合わせ」）。送った人は選ばず、使った API で決まる。
 * 利用停止の申し立ては、種類を増やさずにこれで見分ける。DB には定数名を文字列で保存する
 */
public enum ContactChannel {

    /** ログイン中に送った（POST /api/contacts） */
    NORMAL,
    /** 利用停止中に、利用停止の画面から送った（POST /api/contacts/appeal） */
    SUSPENDED_APPEAL
}
