package com.teamc.isow.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT の設定（application.properties の app.jwt.*）。
 *
 * @param secret     署名用の秘密鍵（Base64）。環境変数 JWT_SECRET から渡す
 * @param expiration トークンの有効期限
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
