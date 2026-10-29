package com.teamc.isow.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 送信全体・1ファイルの上限（spring.servlet.multipart.*）を超えたときの文言（docs/post.md「写真の大きさ」）。
 * MockMvc では multipart の上限が働かないため、例外を受ける部品を直接呼んで確かめる
 */
class UploadTooLargeMessageTest {

    private static final String MESSAGE =
            "写真のファイルが大きすぎます（1枚あたり9MBまで）。写真アプリなどで画像を小さくしてから、もう一度選択してください。";

    @Test
    void 上限を超えた送信には_大きさの上限と_どうすればよいかを伝える() {
        ApiErrorResponse response = new GlobalExceptionHandler(null)
                .handleMaxUploadSize(new MaxUploadSizeExceededException(9L * 1024 * 1024));

        assertThat(response.message()).isEqualTo(MESSAGE);
        assertThat(response.errors()).isEqualTo(Map.of("images", MESSAGE));
    }
}
