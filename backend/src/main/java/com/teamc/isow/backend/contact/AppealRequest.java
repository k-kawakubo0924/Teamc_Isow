package com.teamc.isow.backend.contact;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 利用停止中の申し立て（POST /api/contacts/appeal。docs/settings.md「お問い合わせ」）。
 * トークンの代わりに、利用停止の 403 で受け取った申し立て用の鍵を送る。ほかの項目は ContactRequest と同じ
 *
 * @param appealToken 申し立て用の鍵（SuspensionAppealTokens）
 */
public record AppealRequest(
        @NotBlank(message = "もう一度ログインしてから送ってください")
        String appealToken,

        @Email(message = ContactRequest.EMAIL_MESSAGE)
        @Size(max = 255, message = ContactRequest.EMAIL_MESSAGE)
        String email,

        @NotBlank(message = ContactRequest.CATEGORY_MESSAGE)
        String category,

        @NotBlank(message = "お問い合わせ内容を入力してください")
        @Size(max = ContactInquiry.MAX_BODY_LENGTH, message = "お問い合わせ内容は1000文字以内で入力してください")
        String body) {

    public AppealRequest {
        email = ContactRequest.normalizeEmail(email);
        body = ContactRequest.normalizeBody(body);
    }
}
