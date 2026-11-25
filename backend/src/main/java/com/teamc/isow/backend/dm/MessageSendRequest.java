package com.teamc.isow.backend.dm;

import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * メッセージの送信のリクエスト（POST /api/conversations/{id}/messages。multipart/form-data）。
 * 本文と画像のどちらか一方は必須（両方でもよい）。画像の中身の検証は ImageUploadService で行う。
 *
 * @param body 本文（任意）。前後の空白を除いて空なら、本文なしとして扱う
 * @param image 画像（任意。1枚）
 */
public record MessageSendRequest(
        @Size(max = MAX_BODY_LENGTH, message = "メッセージは" + MAX_BODY_LENGTH + "文字以内で入力してください")
        String body,

        MultipartFile image) {

    /** messages.body の列の長さと同じ */
    static final int MAX_BODY_LENGTH = 1000;

    /** 前後の空白を除いた本文。空なら null */
    String strippedBody() {
        return body == null || body.isBlank() ? null : body.strip();
    }

    boolean hasImage() {
        return image != null && !image.isEmpty();
    }
}
