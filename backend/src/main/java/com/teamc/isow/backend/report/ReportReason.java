package com.teamc.isow.backend.report;

import java.util.EnumSet;
import java.util.Set;

/**
 * 通報の理由（docs/admin.md「通報」）。固定の選択肢とし、DB には定数名を文字列で保存する。
 * 理由は管理者の対応の方針や件数の集計と結びつくため、増やすときはコードを直す（管理画面から増やす仕組みは作らない）。
 * 画面の選択肢（frontend の reportReasons.ts）と合わせること。定数名は既存のデータとの対応に使うため変えないこと
 */
public enum ReportReason {

    SPAM("スパム・宣伝", EnumSet.allOf(ReportTargetType.class)),
    HARASSMENT("嫌がらせ・誹謗中傷", EnumSet.allOf(ReportTargetType.class)),
    SEXUAL("性的な内容", EnumSet.allOf(ReportTargetType.class)),
    VIOLENCE("暴力・危険な内容", EnumSet.allOf(ReportTargetType.class)),
    PERSONAL_INFO("個人情報の掲載", EnumSet.allOf(ReportTargetType.class)),
    /** ユーザーだけ */
    IMPERSONATION("なりすまし", EnumSet.of(ReportTargetType.USER)),
    /** 投稿だけ */
    UNAUTHORIZED_PHOTO("他人の写真の無断転載", EnumSet.of(ReportTargetType.POST)),
    /** 選んだときは、詳しい内容の入力が必須 */
    OTHER("その他", EnumSet.allOf(ReportTargetType.class));

    private final String label;
    private final Set<ReportTargetType> targets;

    ReportReason(String label, Set<ReportTargetType> targets) {
        this.label = label;
        this.targets = targets;
    }

    public String getLabel() {
        return label;
    }

    /** この理由を、targetType の通報で選べるか */
    public boolean appliesTo(ReportTargetType targetType) {
        return targets.contains(targetType);
    }
}
