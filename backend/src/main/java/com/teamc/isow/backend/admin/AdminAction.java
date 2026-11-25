package com.teamc.isow.backend.admin;

/**
 * 管理操作のログの「操作の種類」（docs/admin.md「管理操作のログ」）。
 *
 * <p>DB には定数名を文字列で保存する（AdminOperationLog.action）。{@code @Enumerated} は使わない
 * （Hibernate が値の一覧の検査制約を付け、ddl-auto=update では更新されず、種類を足すと保存できなくなるため。Role と同じ）。
 * 後の段階（通報の対応・利用停止など）の操作も、ここに足していく。定数名は既存のログとの対応に使うため変更しないこと。
 */
public enum AdminAction {

    /** ユーザーを管理者にした（起動時の ADMIN_EMAIL による昇格。操作した人はシステム） */
    USER_PROMOTED_TO_ADMIN,
    /** マスタを追加した */
    MASTER_CREATED,
    /** 既存の手入力のタグを公式タグにした（公式タグの追加で、同じ名前の手入力のタグがあった場合） */
    TAG_MADE_OFFICIAL,
    /** マスタを無効にした */
    MASTER_DEACTIVATED,
    /** 無効にしたマスタを有効に戻した */
    MASTER_ACTIVATED,
    /** お知らせを発行した */
    ANNOUNCEMENT_PUBLISHED,
    /** お知らせを取り消した（行は消さず、利用者側に出さないようにした） */
    ANNOUNCEMENT_WITHDRAWN,
    /** 通報を「対応済み」にした（措置はまだない。状態を記録しただけ） */
    REPORT_RESOLVED,
    /** 通報を「却下」にした */
    REPORT_DISMISSED,
    /** 通報を「未対応」に戻した */
    REPORT_REOPENED,
    /** 通報された DM のメッセージ（と前後）を見た。データは変えないが、DM を見たことは記録する（docs/admin.md「方針」） */
    REPORT_DM_VIEWED,
    /** 投稿を非公開にした（docs/admin.md「6. 措置」） */
    POST_HIDDEN,
    /** 非公開にした投稿を公開に戻した */
    POST_UNHIDDEN,
    /** ユーザーを利用停止にした */
    USER_SUSPENDED,
    /** ユーザーの利用停止を解除した */
    USER_UNSUSPENDED,
    /** お問い合わせを「対応済み」にした（docs/admin.md「7. お問い合わせ」） */
    CONTACT_RESOLVED,
    /** お問い合わせを「対応不要」にした */
    CONTACT_DISMISSED,
    /** お問い合わせを「未対応」に戻した */
    CONTACT_REOPENED
}
