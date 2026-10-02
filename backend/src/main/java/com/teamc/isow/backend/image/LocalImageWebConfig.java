package com.teamc.isow.backend.image;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * ローカルフォルダに保存した画像を、ブラウザから /uploads/** で表示できるようにする。
 * 有効になる条件は LocalImageStorage と同じ。クラウドストレージに差し替えた場合は、画像はそちらの URL から配信されるため無効になる。
 * <img> タグからの読み込みには認証ヘッダーを付けられないため、SecurityConfig で GET /uploads/** を認証なしで許可している
 * （ファイル名は推測できない乱数のため、URL を知らなければ取得できない）。
 */
@Configuration
@ConditionalOnProperty(name = "app.image.storage", havingValue = "local", matchIfMissing = true)
public class LocalImageWebConfig implements WebMvcConfigurer {

    /** 画像を配信する URL のパス（app.image.local.public-base-url の末尾と合わせる） */
    public static final String URL_PATH = "/uploads/";

    private final LocalImageStorage storage;

    public LocalImageWebConfig(LocalImageStorage storage) {
        this.storage = storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // フォルダ外のファイル（../ など）を指すリクエストは Spring 側で拒否される
        registry.addResourceHandler(URL_PATH + "**")
                .addResourceLocations(storage.getBaseDir().toUri().toString());
    }
}
