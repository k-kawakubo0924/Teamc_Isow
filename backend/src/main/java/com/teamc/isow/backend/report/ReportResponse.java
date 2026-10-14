package com.teamc.isow.backend.report;

/**
 * 通報の結果（POST /api/reports）。新しく受け付けたら ACCEPTED（201）、同じ対象をすでに通報していれば ALREADY_REPORTED（200）。
 * 通報された人・ほかに通報した人がいるかは返さない
 */
public record ReportResponse(Status status) {

    public enum Status {
        ACCEPTED,
        ALREADY_REPORTED
    }
}
