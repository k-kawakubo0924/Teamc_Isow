package com.teamc.isow.backend.search;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 検索キーワードの正規化の確認。
 * ここがずれると検索履歴に同じ語が並ぶため、表記ゆれのパターンごとに確認する。
 */
class SearchKeywordNormalizerTest {

    @Test
    void 前後の空白を除き_途中の連続した空白を1つにまとめる() {
        assertThat(SearchKeywordNormalizer.displayKeyword("　 street 　  style  ")).isEqualTo("street style");
    }

    @Test
    void 全角英数字を半角に_半角カナを全角にする() {
        assertThat(SearchKeywordNormalizer.displayKeyword("Ｙ２Ｋ")).isEqualTo("Y2K");
        assertThat(SearchKeywordNormalizer.displayKeyword("ｶｼﾞｭｱﾙ")).isEqualTo("カジュアル");
    }

    @Test
    void 先頭の記号は検索の一部として残す() {
        assertThat(SearchKeywordNormalizer.displayKeyword("#古着")).isEqualTo("#古着");
    }

    @Test
    void 表示する形は大文字小文字を残し_判定用は小文字にそろえる() {
        assertThat(SearchKeywordNormalizer.displayKeyword("Y2K")).isEqualTo("Y2K");
        assertThat(SearchKeywordNormalizer.key("Ｙ２Ｋ")).isEqualTo("y2k");
    }

    @Test
    void 空白だけなら空になる_nullはnullのまま() {
        assertThat(SearchKeywordNormalizer.key(" 　 ")).isEmpty();
        assertThat(SearchKeywordNormalizer.key(null)).isNull();
    }
}
