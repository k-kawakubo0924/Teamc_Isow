package com.teamc.isow.backend.report;

/** 通報の状態を変えられない（409。すでにその状態・同じ対象に未対応の通報がある・証拠を消した後に未対応へ戻す） */
public class AdminReportConflictException extends RuntimeException {

    public AdminReportConflictException(String message) {
        super(message);
    }
}
