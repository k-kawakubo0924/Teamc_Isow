package com.teamc.isow.backend.dm;

import org.springframework.http.HttpStatus;

/**
 * 相談を申し込めない理由（docs/dm.md「相談の申し込み」「拒否と再申請」「相談を受けられる件数の上限」）。
 * 申込（POST /api/conversations）のエラーと、申し込めるかの確認（GET /api/users/{id}/consultation-status）で共通して使う。
 * 画面は定数名（reason）で表示を出し分ける。定数名は画面との取り決めのため変更しないこと。
 * 複数に当てはまる場合は、宣言の順（上のもの）を優先する。
 */
public enum ConsultationUnavailableReason {

    /** 自分自身に申し込もうとした */
    SELF(HttpStatus.BAD_REQUEST, "自分自身には相談を申し込めません。"),
    /** 自分の申請が相手の承認待ち（申請中） */
    ALREADY_REQUESTED(HttpStatus.CONFLICT, "すでに相談を申し込んでいます。"),
    /** 相手からの申請が自分の承認待ち（申請中） */
    REQUEST_RECEIVED(HttpStatus.CONFLICT, "この相手から相談が届いています。"),
    /** 進行中の会話がある */
    IN_PROGRESS(HttpStatus.CONFLICT, "この相手とは相談中です。"),
    /** 自分の申請が拒否されてから24時間以内 */
    REJECTED_RECENTLY(HttpStatus.CONFLICT, "前回の申し込みから24時間は、この相手に再度申し込めません。"),
    /** 相手が受けている「進行中」の会話が上限に達している */
    LIMIT_REACHED(HttpStatus.CONFLICT, "現在、新しい相談を受け付けていません。");

    /** 申込（POST）で返す HTTP ステータス */
    private final HttpStatus httpStatus;

    /** 画面に表示する文言 */
    private final String message;

    ConsultationUnavailableReason(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
