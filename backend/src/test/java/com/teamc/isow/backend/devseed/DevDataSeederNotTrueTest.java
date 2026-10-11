package com.teamc.isow.backend.devseed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 開発用のテストデータの設定が true 以外の値（書き間違いなど）なら、投入の部品を読み込まない（安全側に倒す）。
 * 他のテストのデータと混ざらないよう、別のインメモリ DB を使う
 */
@SpringBootTest(properties = {
    "app.dev-seed.enabled=yes",
    "spring.datasource.url=jdbc:h2:mem:devseed_not_true;DB_CLOSE_DELAY=-1",
})
class DevDataSeederNotTrueTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void trueでなければ投入の部品を読み込まず_データも作らない() {
        assertThat(context.getBeanNamesForType(DevDataSeeder.class)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }
}
