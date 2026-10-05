package com.teamc.isow.backend.dm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** DM・相談に関する設定（app.dm.*）を読み込む */
@Configuration
@EnableConfigurationProperties(DmProperties.class)
public class DmConfig {
}
