package com.teamc.isow.backend.auth;

import com.teamc.isow.backend.config.JwtPurpose;
import com.teamc.isow.backend.config.PurposeJwtDecoder;
import com.teamc.isow.backend.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * メールのリンクに入れる鍵（パスワードの再設定・メールアドレスの確認。docs/auth.md「リンクの鍵」）。
 *
 * <ul>
 *   <li>用途（purpose）を入れた JWT。普通の API では使えない（JwtConfig が purpose 付きを断る）。確かめるときは用途も確かめる</li>
 *   <li>発行した時点のトークンの世代（ver）を入れる。使う側で今の世代と比べ、違えば使えなくする
 *       （パスワードを変えると世代が進むため、再設定の鍵は1回しか使えず、先に送った鍵もまとめて使えなくなる）</li>
 *   <li>メールアドレスなどの個人情報は入れない（中身は誰でも読めるため）</li>
 * </ul>
 */
@Component
public class AccountTokens {

    private final JwtEncoder jwtEncoder;
    private final PurposeJwtDecoder purposeJwtDecoder;

    public AccountTokens(JwtEncoder jwtEncoder, PurposeJwtDecoder purposeJwtDecoder) {
        this.jwtEncoder = jwtEncoder;
        this.purposeJwtDecoder = purposeJwtDecoder;
    }

    /** purpose の鍵を発行する。extraClaims はほかに入れるクレーム（個人情報は入れない） */
    public String issue(String purpose, User user, Duration validity, Map<String, Object> extraClaims) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(validity))
                .claim(JwtPurpose.CLAIM, purpose)
                .claim(JwtTokenService.VERSION_CLAIM, user.getTokenVersion());
        extraClaims.forEach(claims::claim);
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    /** 署名・期限・用途が正しければ中身を返す */
    public Optional<Verified> verify(String token, String purpose) {
        return purposeJwtDecoder.decode(token, purpose).flatMap(jwt -> {
            try {
                return Optional.of(new Verified(Long.valueOf(jwt.getSubject()), version(jwt), jwt));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    private static long version(Jwt jwt) {
        Object version = jwt.getClaims().get(JwtTokenService.VERSION_CLAIM);
        return version instanceof Number number ? number.longValue() : -1;
    }

    /**
     * 確かめた鍵の中身
     *
     * @param tokenVersion 発行した時点のトークンの世代（入っていなければ -1。どの世代とも一致しない）
     */
    public record Verified(Long userId, long tokenVersion, Jwt jwt) {

        /** 今のユーザーの世代と同じか（違えば、パスワードの変更などで使えなくなった鍵） */
        public boolean matches(User user) {
            return user.getId().equals(userId) && user.getTokenVersion() == tokenVersion;
        }
    }
}
