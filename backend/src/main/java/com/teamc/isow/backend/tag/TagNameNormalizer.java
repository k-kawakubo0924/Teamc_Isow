package com.teamc.isow.backend.tag;

import com.teamc.isow.backend.common.NameNormalizer;
import java.util.Locale;

/**
 * タグ名の表記ゆれをそろえる。
 * 選択肢からの登録と投稿時の手入力で同じ処理を通し、同じタグが重複して作られないようにする。
 * マスタの名前と共通の整え方（NameNormalizer）に、タグだけの「先頭の # を除く」を加えたもの
 */
public final class TagNameNormalizer {

    private TagNameNormalizer() {
    }

    /**
     * 表示名として保存する形に整える。
     * 全角英数字を半角・半角カナを全角にし（NFKC）、前後の空白を除き、途中の連続した空白を1つにまとめ、先頭の # を除く。
     * 大文字小文字は入力されたまま残す（Y2K → Y2K）
     */
    public static String displayName(String value) {
        String normalized = NameNormalizer.displayName(value);
        // ハッシュタグとして # を付けて入力されることがあるため、タグだけ先頭の # を除く
        if (normalized != null && normalized.startsWith("#")) {
            normalized = normalized.substring(1).strip();
        }
        return normalized;
    }

    /**
     * 重複判定に使う値。表示名の形に整えたうえで小文字にそろえる（Y2K と y2k を同じタグとして扱う）。
     * ひらがなとカタカナは別の語として扱い、変換しない
     */
    public static String key(String value) {
        String displayName = displayName(value);
        return displayName == null ? null : displayName.toLowerCase(Locale.ROOT);
    }
}
