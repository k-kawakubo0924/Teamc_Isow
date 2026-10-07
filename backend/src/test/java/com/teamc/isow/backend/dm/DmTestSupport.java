package com.teamc.isow.backend.dm;

import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** DM・相談のテストで共通に使う部品 */
final class DmTestSupport {

    /** テストで作るユーザーのメールアドレス（dm-a@example.com 〜 dm-f@example.com） */
    static final String[] EMAILS = {
        "dm-a@example.com", "dm-b@example.com", "dm-c@example.com", "dm-d@example.com", "dm-e@example.com",
        "dm-f@example.com"
    };

    private DmTestSupport() {
    }

    static String token(JwtEncoder jwtEncoder, long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /** テストで作ったデータを消す（messages → conversations → users の順に、参照している側から消す） */
    static void cleanUp(JdbcTemplate jdbcTemplate) {
        // 通知はユーザー・会話・投稿を参照しているため先に消す
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?, ?, ?, ?, ?)", (Object[]) EMAILS);
    }
}
