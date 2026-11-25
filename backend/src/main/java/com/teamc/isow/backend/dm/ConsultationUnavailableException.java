package com.teamc.isow.backend.dm;

/** 相談を申し込めない。HTTP ステータスと応答の文言は reason で決まる（GlobalExceptionHandler） */
public class ConsultationUnavailableException extends RuntimeException {

    private final ConsultationUnavailableReason reason;

    public ConsultationUnavailableException(ConsultationUnavailableReason reason) {
        super("consultation unavailable: " + reason);
        this.reason = reason;
    }

    public ConsultationUnavailableReason getReason() {
        return reason;
    }
}
