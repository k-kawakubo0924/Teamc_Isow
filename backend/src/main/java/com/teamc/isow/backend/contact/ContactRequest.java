package com.teamc.isow.backend.contact;

import com.teamc.isow.backend.auth.AuthInputNormalizer;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * ログイン中のお問い合わせ（POST /api/contacts。docs/settings.md「お問い合わせ」）。
 * 受け取った時点で値をそろえ、入力チェックはそろえた後の値に対して行う
 *
 * @param email 返信先メールアドレス（任意。別の連絡先を使いたい場合に入力する欄。画面は登録済みのアドレスを最初から入れる）
 * @param category 種類（ContactCategory の定数名。ContactService で確かめる）
 * @param body 内容（1000文字まで。改行は \n にそろえてから数える）
 */
public record ContactRequest(
        @Email(message = ContactRequest.EMAIL_MESSAGE)
        @Size(max = 255, message = ContactRequest.EMAIL_MESSAGE)
        String email,

        @NotBlank(message = ContactRequest.CATEGORY_MESSAGE)
        String category,

        @NotBlank(message = "お問い合わせ内容を入力してください")
        @Size(max = ContactInquiry.MAX_BODY_LENGTH, message = "お問い合わせ内容は1000文字以内で入力してください")
        String body) {

    static final String EMAIL_MESSAGE = "メールアドレスの形式が正しくありません";
    static final String CATEGORY_MESSAGE = "お問い合わせの種類を選択してください";

    public ContactRequest {
        email = normalizeEmail(email);
        body = normalizeBody(body);
    }

    /** 空なら null。前後の空白を除き、小文字にそろえる（登録済みのアドレスと比べるため。会員登録と同じ） */
    static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : AuthInputNormalizer.email(email);
    }

    /** 前後の空白を除き、改行を \n にそろえる（\r\n を2文字と数えない。お知らせ・投稿と同じ） */
    static String normalizeBody(String body) {
        return body == null ? null : body.strip().replace("\r\n", "\n").replace('\r', '\n');
    }
}
