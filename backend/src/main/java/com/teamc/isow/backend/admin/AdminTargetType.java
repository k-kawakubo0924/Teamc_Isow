package com.teamc.isow.backend.admin;

/**
 * 管理操作のログの「対象の種類」（docs/admin.md「管理操作のログ」）。対象の ID と組み合わせて、何に対する操作かを表す。
 * DB には定数名を文字列で保存する（理由は AdminAction と同じ）。定数名は変更しないこと。
 */
public enum AdminTargetType {

    /** ユーザー */
    USER,
    /** ファッションの種類（fashion_categories） */
    FASHION_CATEGORY,
    /** 骨格タイプ（body_types） */
    BODY_TYPE,
    /** パーソナルカラー（personal_colors） */
    PERSONAL_COLOR,
    /** タグ（tags） */
    TAG,
    /** お知らせ */
    ANNOUNCEMENT
}
