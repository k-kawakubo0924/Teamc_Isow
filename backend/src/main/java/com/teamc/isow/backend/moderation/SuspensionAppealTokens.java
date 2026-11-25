package com.teamc.isow.backend.moderation;

import com.teamc.isow.backend.config.JwtPurpose;
import com.teamc.isow.backend.config.PurposeJwtDecoder;
import com.teamc.isow.backend.user.User;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * 利用停止中の申し立て用の鍵（docs/settings.md「お問い合わせ」の「利用停止中の申し立て」）。
 *
 * <ul>
 *   <li>利用停止の 403（SuspendedErrorResponse）に入れて渡す。渡すのは、パスワードが正しいログインか、有効なトークンで
 *       API を呼んだときだけなので、受け取れるのは本人だけ</li>
 *   <li>purpose が {@link JwtPurpose#SUSPENSION_APPEAL} の JWT。普通の API では使えない（JwtConfig が purpose 付きを断る）</li>
 *   <li>その時の利用停止（suspended_at）に結びつける。解除された後・別の利用停止になった後は使えない（ContactService で確かめる）</li>
 *   <li>有効期限は1時間（申し立てを書く時間に足りる長さ）。画面はメモリにだけ持つ。切れたら、もう一度ログインすれば受け取れる</li>
 * </ul>
 */
@Component
public class SuspensionAppealTokens {

    /** 鍵の有効期限 */
    static final Duration VALIDITY = Duration.ofHours(1);

    /** 結びつけた利用停止（suspended_at）のクレーム */
    private static final String SUSPENDED_AT_CLAIM = "suspendedAt";

    private final JwtEncoder jwtEncoder;
    private final PurposeJwtDecoder purposeJwtDecoder;

    public SuspensionAppealTokens(JwtEncoder jwtEncoder, PurposeJwtDecoder purposeJwtDecoder) {
        this.jwtEncoder = jwtEncoder;
        this.purposeJwtDecoder = purposeJwtDecoder;
    }

    /** 利用停止中のユーザーに渡す鍵。利用停止中でなければ null */
    public String issue(User user) {
        if (user.getSuspendedAt() == null) {
            return null;
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(VALIDITY))
                .claim(JwtPurpose.CLAIM, JwtPurpose.SUSPENSION_APPEAL)
                .claim(SUSPENDED_AT_CLAIM, suspensionKey(user))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    /** 鍵を確かめる（署名・期限・purpose）。正しくなければ空 */
    public Optional<Claims> verify(String token) {
        return purposeJwtDecoder.decode(token, JwtPurpose.SUSPENSION_APPEAL).flatMap(jwt -> {
            try {
                return Optional.of(new Claims(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString(SUSPENDED_AT_CLAIM)));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * 鍵がこのユーザーの今の利用停止に結びついているか。解除された・期限を過ぎた・別の利用停止になった場合は false。
     * 利用停止中かどうか（isSuspendedAt）は呼ぶ側で確かめる
     */
    public static boolean matchesCurrentSuspension(Claims claims, User user) {
        return user.getSuspendedAt() != null && suspensionKey(user).equals(claims.suspendedAt());
    }

    /** 利用停止の目印。DB の日時の精度の違いで一致しなくならないよう、秒で切り捨てる */
    private static String suspensionKey(User user) {
        return user.getSuspendedAt().truncatedTo(ChronoUnit.SECONDS).toString();
    }

    /** 鍵の中身。userId は申し立てる本人、suspendedAt は結びつけた利用停止の目印 */
    public record Claims(Long userId, String suspendedAt) {
    }
}
