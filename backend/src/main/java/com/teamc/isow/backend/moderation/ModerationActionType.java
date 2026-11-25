package com.teamc.isow.backend.moderation;

/** 措置の種類（docs/admin.md「6. 措置」）。DB には定数名を保存する。定数名は変えないこと */
public enum ModerationActionType {

    HIDE_POST("POST"),
    UNHIDE_POST("POST"),
    SUSPEND_USER("USER"),
    UNSUSPEND_USER("USER");

    private final String targetType;

    ModerationActionType(String targetType) {
        this.targetType = targetType;
    }

    /** 対象の種類（POST・USER） */
    public String getTargetType() {
        return targetType;
    }
}
