package com.teamc.isow.backend.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * JWT の発行（JwtEncoder）と検証（JwtDecoder）に使う部品。
 * 署名方式は HS256 で、発行と検証に同じ秘密鍵を使う。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    /** HS256 に必要な鍵の長さ（256ビット） */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;

    public JwtConfig(JwtProperties properties) {
        this.secretKey = toSecretKey(properties.secret());
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /** 鍵が未設定・短すぎる場合は起動を止める（弱い鍵のまま動かさない） */
    private static SecretKey toSecretKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("環境変数 JWT_SECRET が設定されていません");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET は Base64 で指定してください", e);
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET は " + MIN_SECRET_BYTES + " バイト以上にしてください");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
