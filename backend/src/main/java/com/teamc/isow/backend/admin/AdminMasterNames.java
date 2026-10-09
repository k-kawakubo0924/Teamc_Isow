package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.common.InputValidationException;

/**
 * 管理画面で追加するマスタ・公式タグの名前の確認と、並び順・ログの内容。
 * 表記ゆれのそろえ方は呼び出し元で選ぶ（マスタは NameNormalizer、公式タグは先頭の # も除く TagNameNormalizer）
 */
final class AdminMasterNames {

    /** エラーを返す項目名 */
    static final String FIELD = "name";

    /** 並び順の間隔（初期データと同じく、後から間に入れられるよう 10 ずつ空ける） */
    static final int DISPLAY_ORDER_STEP = 10;

    private AdminMasterNames() {
    }

    /**
     * 表示名の形に整えた名前を確かめて、そのまま返す
     *
     * @throws InputValidationException 空・maxLength 文字を超える場合（項目名は name）
     */
    static String requireValid(String displayName, int maxLength) {
        if (displayName == null || displayName.isEmpty()) {
            throw InputValidationException.of(FIELD, "名前を入力してください");
        }
        if (displayName.length() > maxLength) {
            throw InputValidationException.of(FIELD, "名前は" + maxLength + "文字以内で入力してください");
        }
        return displayName;
    }

    /** 末尾に並べるときの並び順（今の最大値の次。1件もなければ最初の値） */
    static int nextDisplayOrder(Integer currentMax) {
        return currentMax == null ? DISPLAY_ORDER_STEP : currentMax + DISPLAY_ORDER_STEP;
    }

    /** ログの内容 */
    static String detail(String name) {
        return "名前：" + name;
    }
}
