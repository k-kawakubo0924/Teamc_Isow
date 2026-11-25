package com.teamc.isow.backend.common;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 選択肢の名前（マスタ・タグ）の表記ゆれをそろえる共通の処理。
 * タグはこれに加えて先頭の # を除く（TagNameNormalizer）。マスタの名前の # は意味のある文字として残すため、マスタはこちらをそのまま使う
 */
public final class NameNormalizer {

    private NameNormalizer() {
    }

    /**
     * 表示名として保存する形に整える。
     * 全角英数字を半角・半角カナを全角にし（NFKC）、前後の空白を除き、途中の連続した空白を1つにまとめる。
     * 大文字小文字は入力されたまま残す（Y2K → Y2K）
     */
    public static String displayName(String value) {
        if (value == null) {
            return null;
        }
        // NFKC で全角スペースは半角スペースになるため、半角スペースの連続を1つにまとめる
        // （タブ・改行はまとめない。タグ名の整え方の今までの動きのまま）
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip()
                .replaceAll(" +", " ");
    }

    /** 重複判定に使う値。表示名の形に整えたうえで小文字にそろえる（Y2K と y2k を同じ名前として扱う） */
    public static String key(String value) {
        String displayName = displayName(value);
        return displayName == null ? null : displayName.toLowerCase(Locale.ROOT);
    }
}
