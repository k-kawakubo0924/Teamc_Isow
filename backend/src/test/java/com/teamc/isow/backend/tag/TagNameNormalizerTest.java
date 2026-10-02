package com.teamc.isow.backend.tag;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * タグ名の正規化の確認。
 * ここがずれると同じタグが重複して作られるため、表記ゆれのパターンごとに確認する。
 */
class TagNameNormalizerTest {

    @Test
    void 前後の空白を除く_全角スペースも対象() {
        assertThat(TagNameNormalizer.displayName("  古着　")).isEqualTo("古着");
    }

    @Test
    void 途中の連続した空白を1つにまとめる() {
        assertThat(TagNameNormalizer.displayName("street 　  style")).isEqualTo("street style");
    }

    @Test
    void 全角英数字を半角にする() {
        assertThat(TagNameNormalizer.displayName("Ｙ２Ｋ")).isEqualTo("Y2K");
    }

    @Test
    void 半角カナを全角にする() {
        assertThat(TagNameNormalizer.displayName("ｶｼﾞｭｱﾙ")).isEqualTo("カジュアル");
    }

    @Test
    void 先頭の記号を除く_全角も対象() {
        assertThat(TagNameNormalizer.displayName("#古着")).isEqualTo("古着");
        assertThat(TagNameNormalizer.displayName("＃ 古着")).isEqualTo("古着");
    }

    @Test
    void 表示名は大文字小文字を残し_重複判定用は小文字にそろえる() {
        assertThat(TagNameNormalizer.displayName("Y2K")).isEqualTo("Y2K");
        assertThat(TagNameNormalizer.key("Y2K")).isEqualTo("y2k");
        assertThat(TagNameNormalizer.key(" ｙ２ｋ ")).isEqualTo(TagNameNormalizer.key("Y2K"));
    }

    @Test
    void ひらがなとカタカナは別のタグとして扱う() {
        assertThat(TagNameNormalizer.key("かじゅある")).isNotEqualTo(TagNameNormalizer.key("カジュアル"));
    }

    @Test
    void nullはnullのまま返す() {
        assertThat(TagNameNormalizer.displayName(null)).isNull();
        assertThat(TagNameNormalizer.key(null)).isNull();
    }
}
