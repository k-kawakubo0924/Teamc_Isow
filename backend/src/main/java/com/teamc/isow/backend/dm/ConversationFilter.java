package com.teamc.isow.backend.dm;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * DM一覧の絞り込み（GET /api/conversations の status）。
 * 指定がなければ ALL（申請中・進行中・終了）。拒否された会話はどの絞り込みでも出さない。
 */
public enum ConversationFilter {

    ALL(null, Direction.ANY, ConversationStatus.REQUESTED, ConversationStatus.ACTIVE, ConversationStatus.ENDED),
    REQUESTED("requested", Direction.ANY, ConversationStatus.REQUESTED),
    ACTIVE("active", Direction.ANY, ConversationStatus.ACTIVE),
    ENDED("ended", Direction.ANY, ConversationStatus.ENDED),
    /** やり取り中の会話（進行中・終了）。DM一覧の画面で使う */
    CHATS("chats", Direction.ANY, ConversationStatus.ACTIVE, ConversationStatus.ENDED),
    /** 受け取った申請（メッセージリクエスト） */
    RECEIVED("received", Direction.BY_PARTNER, ConversationStatus.REQUESTED),
    /** 自分が送った申請（送信したリクエスト） */
    SENT("sent", Direction.BY_ME, ConversationStatus.REQUESTED);

    /** リクエストで指定する値。ALL は指定しない場合 */
    private final String param;

    /** どちらが申し込んだ会話を含めるか */
    private final Direction direction;

    /** 一覧に含める状態 */
    private final List<ConversationStatus> statuses;

    ConversationFilter(String param, Direction direction, ConversationStatus... statuses) {
        this.param = param;
        this.direction = direction;
        this.statuses = List.of(statuses);
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

    Direction direction() {
        return direction;
    }

    /** どちらが申し込んだ会話を含めるか。code は ConversationRepository.findForList に渡す値 */
    enum Direction {
        ANY(0),
        BY_ME(1),
        BY_PARTNER(2);

        final int code;

        Direction(int code) {
            this.code = code;
        }
    }
}
