package com.teamc.isow.backend.contact;

import java.time.LocalDateTime;
import java.util.List;

/** 管理画面のお問い合わせの応答（/api/admin/contacts/**。docs/admin.md「7. お問い合わせ」） */
public final class AdminContactResponses {

    private AdminContactResponses() {
    }

    public record ListResponse(List<ListItem> items, int page, int size, boolean hasNext) {
    }

    /**
     * 一覧の1行。内容とメールアドレスは出さない（個人情報が書かれうるため、開いた詳細でだけ出す）
     *
     * @param username 送った人の今のユーザー名。退会していれば null
     */
    public record ListItem(Long id, ContactChannel channel, ContactCategory category, ContactStatus status, Long userId,
            String username, LocalDateTime createdAt, LocalDateTime closedAt) {
    }

    /**
     * 詳細
     *
     * @param email 別の連絡先（入力されたときだけ。登録済みのアドレスと同じ・未入力なら null）
     */
    public record Detail(Long id, ContactChannel channel, ContactCategory category, ContactStatus status, Long userId,
            String username, String email, String body, LocalDateTime createdAt, LocalDateTime closedAt,
            String handledByUsername, String adminMemo) {
    }

    public record StatusResult(Long id, ContactStatus status, LocalDateTime closedAt, String handledByUsername,
            String adminMemo) {
    }
}
