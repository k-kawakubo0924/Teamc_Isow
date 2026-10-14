package com.teamc.isow.backend.report;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理者の通報の API（docs/admin.md「通報」）。/api/admin/** は SecurityConfig で管理者だけに許可し、サービスでも確かめる。
 * 措置（非公開化・削除・利用停止）はまだない
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService reportService;

    public AdminReportController(AdminReportService reportService) {
        this.reportService = reportService;
    }

    /** 一覧（対象ごとに1行、新しく通報された順）。status・targetType で絞れる。size は 1〜50 に丸める */
    @GetMapping
    public AdminReportResponses.ListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return reportService.list(jwt.getSubject(), status, targetType, page, size);
    }

    /** 詳細（証拠・通報した人・今の状態）。DM の通報では、見たことをログに残す */
    @GetMapping("/{id:\\d+}")
    public AdminReportResponses.Detail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return reportService.detail(jwt.getSubject(), id);
    }

    /** 状態を変える（対応済み・却下・未対応に戻す）。措置は行わない */
    @PostMapping("/{id:\\d+}/status")
    public AdminReportResponses.StatusResult changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestBody AdminReportStatusRequest request) {
        return reportService.changeStatus(jwt.getSubject(), id, request);
    }

    /**
     * 証拠の写真の中身。証拠の写真は /uploads/** では配信せず、ここだけが管理者であることを確かめてから返す。
     * ブラウザ・途中のサーバーに残さないよう、キャッシュさせない
     */
    @GetMapping("/{id:\\d+}/evidence-files/{fileId:\\d+}")
    public ResponseEntity<byte[]> evidenceFile(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @PathVariable Long fileId) {
        AdminReportService.EvidenceImage image = reportService.evidenceFile(jwt.getSubject(), id, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.content());
    }
}
