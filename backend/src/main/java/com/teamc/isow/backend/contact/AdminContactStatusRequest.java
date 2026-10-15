package com.teamc.isow.backend.contact;

/**
 * お問い合わせの状態の変更（POST /api/admin/contacts/{id}/status）
 *
 * @param status 新しい状態（ContactStatus の定数名）
 * @param memo 管理者のメモ（任意。1000文字まで。管理者にだけ見せ、ログには書かない）
 */
public record AdminContactStatusRequest(String status, String memo) {

    public AdminContactStatusRequest {
        memo = memo == null || memo.isBlank() ? null : memo.strip().replace("\r\n", "\n").replace('\r', '\n');
    }
}
