package com.teamc.isow.backend.contact;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 利用者のお問い合わせ（docs/settings.md「お問い合わせ」）。見る・対応する API は管理者向け（AdminContactController）
 */
@RestController
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    /** ログイン中のお問い合わせ。認証が必要（SecurityConfig の anyRequest）。利用停止中は SuspendedUserFilter で 403 */
    @PostMapping("/api/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse send(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ContactRequest request) {
        return contactService.send(jwt.getSubject(), request);
    }

    /** 利用停止中の申し立て。トークンは不要（SecurityConfig で許可）で、本文の申し立て用の鍵で本人を確かめる */
    @PostMapping("/api/contacts/appeal")
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse appeal(@Valid @RequestBody AppealRequest request) {
        return contactService.appeal(request);
    }
}
