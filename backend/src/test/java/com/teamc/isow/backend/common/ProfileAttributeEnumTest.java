package com.teamc.isow.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 年代・性別の定数名の確認。
 * 定数名は DB に保存される値のため、変更すると既存データが読めなくなる。
 * 定数名の変更でこのテストが落ちた場合は、定数名を元に戻し、表示を変えたいなら label の方を変更すること。
 * 選択肢を追加した場合や label を変更した場合は、ここの期待値も合わせて更新する。
 */
class ProfileAttributeEnumTest {

    @Test
    void 年代のDBに保存する値と表示文言() {
        assertThat(Arrays.stream(AgeGroup.values()).map(Enum::name))
                .containsExactly("TEENS", "EARLY_20S", "LATE_20S", "EARLY_30S", "LATE_30S", "FORTIES", "FIFTIES_AND_OVER");
        assertThat(Arrays.stream(AgeGroup.values()).map(AgeGroup::getLabel))
                .containsExactly("10代", "20代前半", "20代後半", "30代前半", "30代後半", "40代", "50代以上");
    }

    @Test
    void 性別のDBに保存する値と表示文言() {
        assertThat(Arrays.stream(Gender.values()).map(Enum::name))
                .containsExactly("MALE", "FEMALE", "OTHER");
        assertThat(Arrays.stream(Gender.values()).map(Gender::getLabel))
                .containsExactly("男性", "女性", "その他");
    }
}
