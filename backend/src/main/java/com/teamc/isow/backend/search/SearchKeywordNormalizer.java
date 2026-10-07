package com.teamc.isow.backend.search;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 検索キーワードの表記ゆれをそろえる（検索履歴に同じ語が並ばないようにするため）。
 * タグ名の整え方（TagNameNormalizer）と同じだが、先頭の # は検索の一部として残す。
 */
public final class SearchKeywordNormalizer {

    private SearchKeywordNormalizer() {
    }

    /**
     * 表示する形に整える。
     * 全角英数字を半角・半角カナを全角にし（NFKC）、前後の空白を除き、途中の連続した空白を1つにまとめる。
     * 大文字小文字は入力されたまま残す
     */
    public static String displayKeyword(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .strip()
                .replaceAll("\\s+", " ");
    }

    /** 同じキーワードかどうかの判定に使う値。表示する形に整えたうえで小文字にそろえる（Y2K と y2k を同じ語として扱う） */
    public static String key(String value) {
        String displayKeyword = displayKeyword(value);
        return displayKeyword == null ? null : displayKeyword.toLowerCase(Locale.ROOT);
    }
}
