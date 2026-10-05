package com.teamc.isow.backend.dm;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * DM一覧の絞り込み（GET /api/conversations の status）。
 * 指定がなければ ALL（申請中・進行中・終了）。拒否された会話は一覧に出さない。
 */
public enum ConversationFilter {

    ALL(null, List.of(ConversationStatus.REQUESTED, ConversationStatus.ACTIVE, ConversationStatus.ENDED)),
    REQUESTED("requested", List.of(ConversationStatus.REQUESTED)),
    ACTIVE("active", List.of(ConversationStatus.ACTIVE)),
    ENDED("ended", List.of(ConversationStatus.ENDED));

    /** リクエストで指定する値。ALL は指定しない場合 */
    private final String param;

    /** 一覧に含める状態 */
    private final List<ConversationStatus> statuses;

    ConversationFilter(String param, List<ConversationStatus> statuses) {
        this.param = param;
        this.statuses = statuses;
    }

    /** status の値から選ぶ。null・空なら ALL。それ以外で一致しなければ空 */
    public static Optional<ConversationFilter> fromParam(String value) {
        if (value == null || value.isEmpty()) {
            return Optional.of(ALL);
        }
        return Arrays.stream(values()).filter(f -> value.equals(f.param)).findFirst();
    }

    /** 一覧に含める状態の定数名（DB に保存している値） */
    List<String> statusNames() {
        return statuses.stream().map(ConversationStatus::name).toList();
    }
}
