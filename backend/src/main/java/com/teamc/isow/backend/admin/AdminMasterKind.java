package com.teamc.isow.backend.admin;

import java.util.Arrays;
import java.util.Optional;

/**
 * 管理画面で共通の処理（AdminMasterService）を使って管理するマスタの種類（docs/admin.md「マスタの管理」）。
 * 3つとも MasterEntity を継承した同じ作り。公式タグは手入力のタグと同じ表で持つため、ここには含めず別に扱う（AdminOfficialTagService）
 */
public enum AdminMasterKind {

    FASHION_CATEGORY("fashion-categories", AdminTargetType.FASHION_CATEGORY, "ファッションの種類"),
    BODY_TYPE("body-types", AdminTargetType.BODY_TYPE, "骨格タイプ"),
    PERSONAL_COLOR("personal-colors", AdminTargetType.PERSONAL_COLOR, "パーソナルカラー");

    /** URL（/api/admin/masters/{kind}）での名前 */
    private final String path;
    /** 管理操作のログに残す対象の種類 */
    private final AdminTargetType targetType;
    /** エラーの文言に使う名前 */
    private final String label;

    AdminMasterKind(String path, AdminTargetType targetType, String label) {
        this.path = path;
        this.targetType = targetType;
        this.label = label;
    }

    /** URL での名前から探す。ない場合は空 */
    public static Optional<AdminMasterKind> fromPath(String path) {
        return Arrays.stream(values()).filter(kind -> kind.path.equals(path)).findFirst();
    }

    public String getPath() {
        return path;
    }

    public AdminTargetType getTargetType() {
        return targetType;
    }

    public String getLabel() {
        return label;
    }
}
