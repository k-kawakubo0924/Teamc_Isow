package com.teamc.isow.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamc.isow.backend.tag.TagNameNormalizer;
import org.junit.jupiter.api.Test;

/** 選択肢の名前（マスタ・タグ）に共通の正規化の確認。タグ名の正規化は TagNameNormalizerTest */
class NameNormalizerTest {

    @Test
    void 全角半角と空白をそろえる() {
        assertThat(NameNormalizer.displayName("　ＭＯＤＥ　 ｶｼﾞｭｱﾙ ")).isEqualTo("MODE カジュアル");
        assertThat(NameNormalizer.key("ＭＯＤＥ")).isEqualTo("mode");
    }

    @Test
    void 先頭の記号は削らない_タグ名の正規化だけが削る() {
        assertThat(NameNormalizer.displayName("＃ストリート")).isEqualTo("#ストリート");
        assertThat(NameNormalizer.key("#A")).isNotEqualTo(NameNormalizer.key("A"));

        assertThat(TagNameNormalizer.displayName("＃ストリート")).isEqualTo("ストリート");
    }

    @Test
    void nullはnullのまま() {
        assertThat(NameNormalizer.displayName(null)).isNull();
        assertThat(NameNormalizer.key(null)).isNull();
    }
}
