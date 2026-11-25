package com.teamc.isow.backend.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** メールの設定（app.mail.*）と、メールを送るスレッド（docs/auth.md「メールの送信」） */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    /**
     * メールを送るスレッド。app.mail.async が true なら別のスレッド（2本。順番待ちは100通まで）、false なら頼んだスレッドで送る（テスト用）。
     * 名前を付けて、ほかの TaskExecutor と取り違えないようにする
     */
    @Bean
    public TaskExecutor mailExecutor(MailProperties properties) {
        if (!properties.async()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("mail-");
        executor.initialize();
        return executor;
    }
}
