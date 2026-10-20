package com.teamc.isow.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * このアプリのログ（com.teamc.isow のロガー）だけを集める部品の確認。
 * 「アプリのログに〇〇を書かない」テストが、MockMvc の出力（失敗したテストのリクエストの本文を標準出力に書く）や、
 * ほかのテストのログに巻き込まれて落ちないようにするため（docs/auth.md「直した課題：ログのテストが…」）
 */
@ExtendWith(AppLogExtension.class)
class AppLogTest {

    private static final Logger APP = LoggerFactory.getLogger("com.teamc.isow.backend.applogtest.Sample");

    @Test
    void このアプリのロガーが書いたログは入る(AppLog log) {
        APP.info("sent to {}", "app-log@example.com");

        assertThat(log.text()).contains("sent to app-log@example.com");
    }

    @Test
    void 例外のスタックトレースの中身も入る(AppLog log) {
        APP.warn("failed", new IllegalStateException("leaked-in-exception@example.com"));

        assertThat(log.text()).contains("failed", "leaked-in-exception@example.com");
    }

    @Test
    void 標準出力に直接書いたもの_MockMvcの出力_は入らない(AppLog log) {
        System.out.println("Body = {\"email\":\"mockmvc-print@example.com\"}");

        assertThat(log.text()).doesNotContain("mockmvc-print@example.com");
    }

    @Test
    void ほかのライブラリのロガーが書いたログは入らない(AppLog log) {
        LoggerFactory.getLogger("org.springframework.applogtest").warn("library-log@example.com");

        assertThat(log.text()).doesNotContain("library-log@example.com");
    }

    @Test
    void 集め終わった後と_集め始める前に書いたログは入らない() {
        AppLog before = new AppLog();
        before.start();
        APP.info("first-test@example.com");
        before.stop();
        APP.info("between-tests@example.com");

        AppLog after = new AppLog();
        after.start();
        APP.info("second-test@example.com");
        after.stop();

        assertThat(before.text()).contains("first-test@example.com").doesNotContain("between-tests@example.com");
        assertThat(after.text()).contains("second-test@example.com")
                .doesNotContain("first-test@example.com", "between-tests@example.com");
    }
}
