package com.teamc.isow.backend.notification;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知（docs/notification.md）。ログイン後の画面でのみ使うため認証が必要（SecurityConfig の anyRequest）。
 * 自分の通知だけを扱い、他人の通知・存在しない通知はどちらも 404 にする
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    public NotificationController(NotificationQueryService notificationQueryService) {
        this.notificationQueryService = notificationQueryService;
    }

    /**
     * 自分の通知一覧（通知日時の新しい順）。メッセージの通知も含む。
     * ページ番号で区切るため、読み込みの途中で通知が増えると、同じ通知が次のページにも出ることがある。
     * 画面側で id が重複したものを省くこと
     */
    @GetMapping
    public NotificationListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return notificationQueryService.list(jwt.getSubject(), page, size);
    }

    /** 未読件数（ベルのバッジ用。メッセージの通知は含めない） */
    @GetMapping("/summary")
    public NotificationSummaryResponse summary(@AuthenticationPrincipal Jwt jwt) {
        return notificationQueryService.summary(jwt.getSubject());
    }

    /** 既読にする。id は数字のみ（それ以外は URL が一致せず 404） */
    @PostMapping("/{id:\\d+}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        notificationQueryService.markRead(jwt.getSubject(), id);
    }

    /** 未読に戻す */
    @PostMapping("/{id:\\d+}/unread")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markUnread(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        notificationQueryService.markUnread(jwt.getSubject(), id);
    }

    /** 削除する */
    @DeleteMapping("/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        notificationQueryService.delete(jwt.getSubject(), id);
    }

    /** 自分の未読の通知をすべて既読にする（メッセージの通知も含む） */
    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notificationQueryService.markAllRead(jwt.getSubject());
    }
}
