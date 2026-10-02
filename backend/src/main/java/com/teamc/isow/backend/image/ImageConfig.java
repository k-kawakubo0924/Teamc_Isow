package com.teamc.isow.backend.image;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 画像の保存に関する設定（app.image.*）を読み込む */
@Configuration
@EnableConfigurationProperties(ImageProperties.class)
public class ImageConfig {
}
