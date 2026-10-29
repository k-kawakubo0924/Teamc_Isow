package com.teamc.isow.backend.image;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * アップロードの上限の設定（src/main/resources/application.properties）の確認（docs/post.md「写真の大きさ」）。
 * テストは別の設定ファイル（src/test/resources）を読むため、本番と同じ設定ファイルを直接読んで確かめる
 */
class UploadLimitsTest {

    /** 公開時の入口（Cloudflare）が受け付ける1リクエストの上限。無料プランの 100MB を、少ないほうの数え方（10^6）で見る */
    private static final long CLOUDFLARE_MAX_REQUEST_BYTES = 100_000_000L;

    /** 投稿1件の写真の枚数の上限 */
    private static final int MAX_IMAGES_PER_POST = 10;

    /** 写真以外の部分（投稿の文字・multipart の区切り）に見込む余裕 */
    private static final long TEXT_MARGIN_BYTES = DataSize.ofMegabytes(1).toBytes();

    @Test
    void 写真を上限の大きさで10枚送っても_Cloudflareの1リクエスト100MBに収まる() throws IOException {
        Properties main = load("src/main/resources/application.properties");
        long maxFile = DataSize.parse(main.getProperty("app.image.max-file-size")).toBytes();
        long maxRequest = DataSize.parse(main.getProperty("spring.servlet.multipart.max-request-size")).toBytes();

        // 写真10枚と文字が、サーバーの上限に収まる（上限の写真10枚の投稿が、サーバーで断られない）
        assertThat(maxRequest).isGreaterThanOrEqualTo(maxFile * MAX_IMAGES_PER_POST + TEXT_MARGIN_BYTES);
        // サーバーの上限は Cloudflare の上限より小さい（大きすぎる送信は、Cloudflare ではなくこのアプリが断り、アプリの文言を出す）
        assertThat(maxRequest).isLessThan(CLOUDFLARE_MAX_REQUEST_BYTES);
    }

    @Test
    void 写真1枚あたりの上限は9MBで_テスト用の設定も同じ値にしている() throws IOException {
        Properties main = load("src/main/resources/application.properties");
        Properties test = load("src/test/resources/application.properties");

        assertThat(DataSize.parse(main.getProperty("app.image.max-file-size"))).isEqualTo(DataSize.ofMegabytes(9));
        assertThat(test.getProperty("app.image.max-file-size")).isEqualTo(main.getProperty("app.image.max-file-size"));
        assertThat(main.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("${app.image.max-file-size}");
    }

    private static Properties load(String path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(path), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }
}
