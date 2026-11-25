package com.teamc.isow.backend.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamc.isow.backend.master.BodyTypeRepository;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.master.PersonalColorRepository;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 初期データ投入の確認。
 * 起動時（テストのコンテキスト作成時）に一度投入されている状態から、再実行しても重複・上書きしないことを確かめる。
 * 各テストはトランザクション内で行い、終了時にロールバックする（他のテストに影響させない）。
 */
@SpringBootTest
@Transactional
class MasterDataSeederTest {

    @Autowired
    private MasterDataSeeder seeder;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private BodyTypeRepository bodyTypeRepository;

    @Autowired
    private PersonalColorRepository personalColorRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private SeedHistoryRepository seedHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 起動時に初期データが登録されている() {
        assertThat(fashionCategoryRepository.count()).isEqualTo(MasterSeedData.FASHION_CATEGORIES.size());
        assertThat(bodyTypeRepository.count()).isEqualTo(MasterSeedData.BODY_TYPES.size());
        assertThat(personalColorRepository.count()).isEqualTo(MasterSeedData.PERSONAL_COLORS.size());
        assertThat(tagRepository.findAll()).hasSize(MasterSeedData.OFFICIAL_TAGS.size()).allMatch(Tag::isOfficial);
    }

    @Test
    void 再実行しても重複して登録されない() {
        seeder.run(null);
        seeder.run(null);

        assertThat(fashionCategoryRepository.count()).isEqualTo(MasterSeedData.FASHION_CATEGORIES.size());
        assertThat(bodyTypeRepository.count()).isEqualTo(MasterSeedData.BODY_TYPES.size());
        assertThat(personalColorRepository.count()).isEqualTo(MasterSeedData.PERSONAL_COLORS.size());
        assertThat(tagRepository.count()).isEqualTo(MasterSeedData.OFFICIAL_TAGS.size());
    }

    @Test
    void 名前を変更した行は元に戻されず_元の名前で再登録もされない() {
        jdbcTemplate.update("UPDATE fashion_categories SET name = 'キレイめ' WHERE name = 'きれいめ'");

        seeder.run(null);

        assertThat(fashionCategoryRepository.existsByName("キレイめ")).isTrue();
        assertThat(fashionCategoryRepository.existsByName("きれいめ")).isFalse();
        assertThat(fashionCategoryRepository.count()).isEqualTo(MasterSeedData.FASHION_CATEGORIES.size());
    }

    @Test
    void 無効化した行は有効に戻されない() {
        jdbcTemplate.update("UPDATE body_types SET is_active = false WHERE name = 'ウェーブ'");

        seeder.run(null);

        Boolean active = jdbcTemplate.queryForObject(
                "SELECT is_active FROM body_types WHERE name = 'ウェーブ'", Boolean.class);
        assertThat(active).isFalse();
    }

    @Test
    void 未投入の初期データだけが登録される() {
        // 後から一覧に追加された状況を、履歴と行を消して再現する
        jdbcTemplate.update("DELETE FROM personal_colors WHERE name = 'ブルベ冬'");
        seedHistoryRepository.deleteById("personal_color:ブルベ冬");
        seedHistoryRepository.flush();

        seeder.run(null);

        assertThat(personalColorRepository.existsByName("ブルベ冬")).isTrue();
        assertThat(personalColorRepository.count()).isEqualTo(MasterSeedData.PERSONAL_COLORS.size());
    }

    @Test
    void 同じ名前の行が手動で登録済みなら_行は追加せず履歴だけ記録する() {
        // 手動で登録した行がある状況を、履歴だけ消して再現する
        seedHistoryRepository.deleteById("tag:古着");
        seedHistoryRepository.flush();

        seeder.run(null);

        assertThat(tagRepository.count()).isEqualTo(MasterSeedData.OFFICIAL_TAGS.size());
        assertThat(seedHistoryRepository.existsById("tag:古着")).isTrue();
    }
}
