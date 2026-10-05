package com.teamc.isow.backend.dm;

import org.springframework.http.HttpStatus;

/**
 * 会話の承認・拒否・終了ができない理由。画面は定数名（reason）で表示を出し分ける。
 * 定数名は画面との取り決めのため変更しないこと。
 * 会話が存在しない・当事者でない場合は、会話の有無が分からないよう ConversationNotFoundException（404）にする。
 */
public enum ConversationOperationError {

    /** 申し込んだ本人が承認・拒否しようとした */
    NOT_RECIPIENT(HttpStatus.FORBIDDEN),
    /** 会話の状態が操作に合わない（終了した会話を承認するなど） */
    INVALID_STATUS(HttpStatus.CONFLICT),
    /** 承認する時点で、自分が受けている「進行中」の会話が上限に達している */
    LIMIT_REACHED(HttpStatus.CONFLICT);

    private final HttpStatus httpStatus;

    ConversationOperationError(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
