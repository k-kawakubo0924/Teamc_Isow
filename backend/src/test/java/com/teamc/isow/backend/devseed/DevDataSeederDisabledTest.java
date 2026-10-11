package com.teamc.isow.backend.devseed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 開発用のテストデータは、既定では投入しない（README「開発用のテストデータ」）。
 * 設定がなければ、投入の部品そのものを読み込まない（他のテストと同じ既定の状態で確かめる）
 */
@SpringBootTest
class DevDataSeederDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 設定がなければ投入の部品を読み込まず_データも作らない() {
        assertThat(context.getBeanNamesForType(DevDataSeeder.class)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email = 'dev_aoi@example.com'", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM master_seed_history WHERE seed_key = 'dev_seed:v1'", Integer.class))
                .isZero();
    }
}
