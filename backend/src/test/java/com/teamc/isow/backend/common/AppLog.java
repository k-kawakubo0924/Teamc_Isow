package com.teamc.isow.backend.common;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.LoggerFactory;

/**
 * テストの間に、このアプリのロガー（{@code com.teamc.isow} とその下）が書いたログだけを集める（{@link AppLogExtension} で使う）。
 *
 * <p>「アプリのログに〇〇を書かない」ことを確かめるのに使う。標準出力をまるごと見る {@code CapturedOutput} では、
 * MockMvc の出力（失敗したテストのリクエストを本文ごと標準出力に書く）や、同じクラスの先に走ったテストの出力まで含まれ、
 * ほかのテストの失敗に巻き込まれて落ちるため（docs/auth.md「直した課題：ログのテストが、ほかのテストの失敗に巻き込まれて落ちる」）。
 *
 * <ul>
 *   <li>ロガーから直接集めるので、MockMvc の出力（ロガーを通らない）は入らない</li>
 *   <li>例外のスタックトレースの中身も入る（例外の message にアドレスなどを入れていないかも確かめられる）</li>
 *   <li>ほかのライブラリ（Spring・Hibernate など）のロガーが書いたログは入らない（見張るのはこのアプリのログだけ）</li>
 * </ul>
 */
public final class AppLog {

    private static final String APP_LOGGER = "com.teamc.isow";

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    void start() {
        appender.start();
        appLogger().addAppender(appender);
    }

    void stop() {
        appLogger().detachAppender(appender);
        appender.stop();
    }

    /** 集めたログ（1件ごとに、ロガー名・レベル・本文、例外があればスタックトレース） */
    public String text() {
        List<ILoggingEvent> events = List.copyOf(appender.list);
        return events.stream().map(AppLog::format).collect(Collectors.joining(System.lineSeparator()));
    }

    private static String format(ILoggingEvent event) {
        String line = event.getLoggerName() + " " + event.getLevel() + " " + event.getFormattedMessage();
        if (event.getThrowableProxy() == null) {
            return line;
        }
        return line + System.lineSeparator() + ThrowableProxyUtil.asString(event.getThrowableProxy());
    }

    private static Logger appLogger() {
        return (Logger) LoggerFactory.getLogger(APP_LOGGER);
    }
}
