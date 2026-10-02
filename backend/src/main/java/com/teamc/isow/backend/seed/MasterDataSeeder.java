package com.teamc.isow.backend.seed;

import com.teamc.isow.backend.master.BodyType;
import com.teamc.isow.backend.master.BodyTypeRepository;
import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.master.PersonalColor;
import com.teamc.isow.backend.master.PersonalColorRepository;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagNameNormalizer;
import com.teamc.isow.backend.tag.TagRepository;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 起動時に、MasterSeedData の初期データのうち未投入のものだけを登録する。
 *
 * <p>投入済みかどうかは名前の一致ではなく投入履歴（master_seed_history）で判定する。
 * 名前の一致で判定すると、投入後に名前を直した場合に元の名前で再登録されてしまうため。
 * 既存の行は更新しない（名前の変更や無効化をしていても、起動のたびに元へ戻さない）。
 */
@Component
public class MasterDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MasterDataSeeder.class);

    private final SeedHistoryRepository seedHistoryRepository;
    private final FashionCategoryRepository fashionCategoryRepository;
    private final BodyTypeRepository bodyTypeRepository;
    private final PersonalColorRepository personalColorRepository;
    private final TagRepository tagRepository;

    public MasterDataSeeder(
            SeedHistoryRepository seedHistoryRepository,
            FashionCategoryRepository fashionCategoryRepository,
            BodyTypeRepository bodyTypeRepository,
            PersonalColorRepository personalColorRepository,
            TagRepository tagRepository) {
        this.seedHistoryRepository = seedHistoryRepository;
        this.fashionCategoryRepository = fashionCategoryRepository;
        this.bodyTypeRepository = bodyTypeRepository;
        this.personalColorRepository = personalColorRepository;
        this.tagRepository = tagRepository;
    }

    /** 途中で失敗した場合に中途半端な状態で残らないよう、全体を1つのトランザクションで行う */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed("fashion_category", MasterSeedData.FASHION_CATEGORIES,
                fashionCategoryRepository::existsByName,
                item -> fashionCategoryRepository.save(new FashionCategory(item.name(), item.displayOrder())));
        seed("body_type", MasterSeedData.BODY_TYPES,
                bodyTypeRepository::existsByName,
                item -> bodyTypeRepository.save(new BodyType(item.name(), item.displayOrder())));
        seed("personal_color", MasterSeedData.PERSONAL_COLORS,
                personalColorRepository::existsByName,
                item -> personalColorRepository.save(new PersonalColor(item.name(), item.displayOrder())));
        // タグは表記ゆれ（大文字小文字など）も同じタグとみなすため、正規化した値で既存の行を探す
        seed("tag", MasterSeedData.OFFICIAL_TAGS,
                name -> tagRepository.existsByNormalizedName(TagNameNormalizer.key(name)),
                item -> tagRepository.save(Tag.official(item.name(), item.displayOrder())));
    }

    /**
     * 履歴にない初期データを登録し、履歴に記録する。
     * 同じ名前の行が既にある（手動で登録済みなど）場合は、行は追加・更新せず履歴だけ記録する
     */
    private void seed(String type, List<MasterSeedData.Item> items,
            Predicate<String> existsByName, Consumer<MasterSeedData.Item> insert) {
        int inserted = 0;
        for (MasterSeedData.Item item : items) {
            String seedKey = type + ":" + item.name();
            if (seedHistoryRepository.existsById(seedKey)) {
                continue;
            }
            if (!existsByName.test(item.name())) {
                insert.accept(item);
                inserted++;
            }
            seedHistoryRepository.save(new SeedHistory(seedKey));
        }
        if (inserted > 0) {
            log.info("初期データを登録しました: {} {}件", type, inserted);
        }
    }
}
