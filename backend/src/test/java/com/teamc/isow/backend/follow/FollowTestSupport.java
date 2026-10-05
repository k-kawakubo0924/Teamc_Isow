package com.teamc.isow.backend.follow;

import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/** フォローのテストで共通に使う部品 */
final class FollowTestSupport {

    static final String EMAIL_A = "follow-a@example.com";
    static final String EMAIL_B = "follow-b@example.com";
    static final String EMAIL_C = "follow-c@example.com";

    private FollowTestSupport() {
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

    /** テストで作ったデータを消す（follows がユーザーを参照しているため先に消す） */
    static void cleanUp(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("DELETE FROM follows");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?, ?)", EMAIL_A, EMAIL_B, EMAIL_C);
    }
}
