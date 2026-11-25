package com.teamc.isow.backend.admin;

/**
 * 公式タグの追加の結果
 *
 * @param madeOfficial true なら、新しく作らずに同じ名前の手入力のタグを公式にした
 *     （画面で「既存のタグを公式にしました」と出す。表示名は既存のタグの表記のまま）
 */
public record AdminOfficialTagCreateResponse(AdminMasterItem tag, boolean madeOfficial) {
}
