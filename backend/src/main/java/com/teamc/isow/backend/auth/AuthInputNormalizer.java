package com.teamc.isow.backend.auth;

import java.util.Locale;

/**
 * 認証まわりの入力値を、保存・照合の前にそろえる。
 * 登録とログインで同じ処理を通すことで、照合結果がずれないようにする。
 */
public final class AuthInputNormalizer {

    private AuthInputNormalizer() {
    }

    /** 前後の空白を除き、小文字にそろえる（大文字小文字違いの重複登録を防ぐ） */
    public static String email(String value) {
        return value == null ? null : value.strip().toLowerCase(Locale.ROOT);
    }

    /** 前後の空白とハイフンを除く（090-1234-5678 → 09012345678） */
    public static String phoneNumber(String value) {
        return value == null ? null : value.strip().replace("-", "");
    }

    /** 前後の空白と、先頭の @ を除く（@yuu_style → yuu_style） */
    public static String username(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.startsWith("@") ? stripped.substring(1) : stripped;
    }
}
