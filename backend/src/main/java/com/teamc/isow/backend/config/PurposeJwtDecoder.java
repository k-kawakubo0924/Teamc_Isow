package com.teamc.isow.backend.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * 用途を限った JWT（{@link JwtPurpose}）を確かめる。署名・有効期限に加えて、purpose が決まった値であることを確かめる。
 *
 * <p>JwtDecoder を実装しない（Spring Security が普通の API の検証に使う JwtDecoder が2つになり、どちらを使うか決まらなくなるため）
 */
public class PurposeJwtDecoder {

    private final Map<String, NimbusJwtDecoder> decoders;

    PurposeJwtDecoder(SecretKey secretKey) {
        // 用途ごとに検証の部品を作る（申し立て・パスワードの再設定・メールアドレスの確認）
        Map<String, NimbusJwtDecoder> byPurpose = new HashMap<>();
        for (String purpose : List.of(JwtPurpose.SUSPENSION_APPEAL, JwtPurpose.PASSWORD_RESET, JwtPurpose.EMAIL_CHANGE)) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefault(), JwtPurpose.require(purpose)));
            byPurpose.put(purpose, decoder);
        }
        this.decoders = Map.copyOf(byPurpose);
    }

    /** purpose が一致し、署名・期限が正しければ中身を返す。そうでなければ空 */
    public Optional<Jwt> decode(String token, String purpose) {
        NimbusJwtDecoder decoder = decoders.get(purpose);
        if (decoder == null || token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(decoder.decode(token));
        } catch (JwtException e) {
            return Optional.empty();
        }
    }
}
