package com.teamc.isow.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** もう一度試せるまでの残り時間（429・「あと約N分」・Retry-After）の共通の扱い */
class RetryLaterExceptionTest {

    /** 文言に分をそのまま入れるだけの、テスト用の例外 */
    private static class TestRetryLater extends RetryLaterException {
        TestRetryLater(Duration retryAfter) {
            super("test", retryAfter);
        }

        @Override
        public String userMessage() {
            return "あと約" + retryAfterMinutes() + "分で、もう一度お試しください。";
        }
    }

    private static TestRetryLater after(Duration retryAfter) {
        return new TestRetryLater(retryAfter);
    }

    @Test
    void 秒は端数を切り上げる() {
        assertThat(after(Duration.ofSeconds(90)).retryAfterSeconds()).isEqualTo(90);
        assertThat(after(Duration.ofMillis(1500)).retryAfterSeconds()).isEqualTo(2);
    }

    @Test
    void 一秒に満たない_0や負の残り時間は一秒として扱う() {
        assertThat(after(Duration.ofMillis(200)).retryAfterSeconds()).isEqualTo(1);
        assertThat(after(Duration.ZERO).retryAfterSeconds()).isEqualTo(1);
        assertThat(after(Duration.ofSeconds(-5)).retryAfterSeconds()).isEqualTo(1);
    }

    @Test
    void 分は端数を切り上げ_一分に満たなくても一分() {
        assertThat(after(Duration.ofSeconds(1)).retryAfterMinutes()).isEqualTo(1);
        assertThat(after(Duration.ofSeconds(60)).retryAfterMinutes()).isEqualTo(1);
        assertThat(after(Duration.ofSeconds(61)).retryAfterMinutes()).isEqualTo(2);
        assertThat(after(Duration.ofMinutes(15)).retryAfterMinutes()).isEqualTo(15);
    }

    @Test
    void 応答は429で_Retry_Afterは秒_本文は例外ごとの文言() {
        ResponseEntity<ApiErrorResponse> response =
                new GlobalExceptionHandler(null).handleRetryLater(after(Duration.ofSeconds(61)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("61");
        assertThat(response.getBody()).isEqualTo(new ApiErrorResponse("あと約2分で、もう一度お試しください。", Map.of()));
    }
}
