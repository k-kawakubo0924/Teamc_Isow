package com.teamc.isow.backend.report;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 利用者の通報（docs/admin.md「通報」）。ログイン後の画面でのみ使うため認証が必要（SecurityConfig の anyRequest）。
 * 通報を見る・対応する API は管理者向け（/api/admin/reports/**。AdminReportController）
 */
@RestController
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** 通報する。新しく受け付けたら 201、同じ対象をすでに通報していれば 200（ALREADY_REPORTED） */
    @PostMapping("/api/reports")
    public ResponseEntity<ReportResponse> report(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReportRequest request) {
        ReportResponse response = reportService.report(jwt.getSubject(), request);
        HttpStatus status = response.status() == ReportResponse.Status.ACCEPTED ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
