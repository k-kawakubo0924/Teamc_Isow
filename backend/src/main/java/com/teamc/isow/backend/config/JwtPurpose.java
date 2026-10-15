package com.teamc.isow.backend.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 用途を限った JWT（ログイン用のトークンとは別のもの）の purpose クレーム（docs/settings.md「お問い合わせ」）。
 *
 * <p>ログイン用のトークンには purpose を入れない。普通の API の検証（JwtConfig の JwtDecoder）は、
 * purpose があるトークンを必ず断る（{@link #rejectAny()}）。これがないと、たとえば申し立て用の鍵が、
 * 利用停止を解除した後にログイン用のトークンとして使えてしまう。用途を限ったトークンは {@link PurposeJwtDecoder} で確かめる
 */
public final class JwtPurpose {

    /** クレームの名前 */
    public static final String CLAIM = "purpose";

    /** 利用停止中の申し立て（POST /api/contacts/appeal）だけに使える鍵 */
    public static final String SUSPENSION_APPEAL = "suspension_appeal";

    private JwtPurpose() {
    }

    /** purpose があるトークンを断る（普通の API 用） */
    static OAuth2TokenValidator<Jwt> rejectAny() {
        return jwt -> jwt.hasClaim(CLAIM)
                ? OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "用途を限ったトークンは使えません", null))
                : OAuth2TokenValidatorResult.success();
    }

    /** purpose が expected のトークンだけを通す */
    static OAuth2TokenValidator<Jwt> require(String expected) {
        return jwt -> expected.equals(jwt.getClaimAsString(CLAIM))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "用途が違うトークンです", null));
    }
}
