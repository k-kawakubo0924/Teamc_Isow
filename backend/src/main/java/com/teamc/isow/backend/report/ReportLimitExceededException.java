package com.teamc.isow.backend.report;

/** 1人1日の通報の上限（ReportService.MAX_REPORTS_PER_DAY）を超えた（429） */
public class ReportLimitExceededException extends RuntimeException {

    public ReportLimitExceededException() {
        super("report limit exceeded");
    }
}
