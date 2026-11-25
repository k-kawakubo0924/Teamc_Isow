package com.teamc.isow.backend.profile;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * プロフィール編集のリクエスト（PUT /api/users/me。JSON）。画面上部の「保存」でまとめて保存するため、全項目を置き換える。
 * 名前以外は任意で、null を送ると未設定に戻す。受け取った時点で名前の前後の空白を除く。
 * 選択肢が存在するか（性別・年代の code、骨格タイプ・パーソナルカラーの id）は ProfileService で確認する。
 *
 * @param displayName 名前（表示名）。必須
 * @param gender 性別の code（GET /api/masters の genders）
 * @param heightCm 身長（cm）。範囲は docs/profile.md の暫定値
 * @param ageGroup 年代の code（GET /api/masters の ageGroups）
 * @param bodyTypeId 骨格タイプの id（GET /api/masters の bodyTypes）
 * @param personalColorId パーソナルカラーの id（GET /api/masters の personalColors）
 */
public record ProfileUpdateRequest(
        @NotBlank(message = "名前を入力してください")
        @Size(max = MAX_DISPLAY_NAME_LENGTH, message = "名前は" + MAX_DISPLAY_NAME_LENGTH + "文字以内で入力してください")
        String displayName,

        String gender,

        @Min(value = MIN_HEIGHT_CM, message = HEIGHT_MESSAGE)
        @Max(value = MAX_HEIGHT_CM, message = HEIGHT_MESSAGE)
        Integer heightCm,

        String ageGroup,

        Long bodyTypeId,

        Long personalColorId) {

    /** users.display_name の列の長さと同じ */
    static final int MAX_DISPLAY_NAME_LENGTH = 50;

    /** 身長の範囲（暫定。docs/profile.md） */
    static final int MIN_HEIGHT_CM = 100;
    static final int MAX_HEIGHT_CM = 250;
    private static final String HEIGHT_MESSAGE = "身長は" + MIN_HEIGHT_CM + "〜" + MAX_HEIGHT_CM + "cmの範囲で入力してください";

    public ProfileUpdateRequest {
        displayName = displayName == null ? null : displayName.strip();
    }
}
