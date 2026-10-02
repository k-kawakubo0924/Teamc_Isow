package com.teamc.isow.backend.auth;

import java.time.Instant;

/**
 * ログインの結果。
 *
 * @param token     以降のリクエストで Authorization: Bearer に付ける JWT
 * @param expiresAt トークンの有効期限（画面側で期限切れを判断するため）
 */
public record LoginResponse(String token, Instant expiresAt) {
}
