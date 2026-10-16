package com.teamc.isow.backend.auth;

import com.teamc.isow.backend.config.JwtProperties;
import com.teamc.isow.backend.user.User;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** ログイン成功時に JWT を発行する */
@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /** トークンの世代のクレーム（docs/auth.md「トークンの世代（全端末のログアウト）」） */
    public static final String VERSION_CLAIM = "ver";

    /**
     * sub にユーザーIDを入れたトークンを発行する。
     * メールアドレスは変更されうるうえ、トークンの中身は誰でも読めるため入れない。
     * トークンの世代（ver）を入れ、世代が進んだら使えなくする（SuspendedUserFilter）
     */
    public LoginResponse issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.expiration());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(VERSION_CLAIM, user.getTokenVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new LoginResponse(token, expiresAt);
    }
}
