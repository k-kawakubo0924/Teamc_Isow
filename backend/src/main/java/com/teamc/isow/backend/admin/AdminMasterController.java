package com.teamc.isow.backend.admin;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * マスタ・公式タグの管理の API（docs/admin.md「マスタの管理」）。/api/admin/** は SecurityConfig で管理者だけに許可している。
 * 存在しない種類・ID は 404
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMasterController {

    private final AdminMasterService masterService;
    private final AdminOfficialTagService officialTagService;

    public AdminMasterController(AdminMasterService masterService, AdminOfficialTagService officialTagService) {
        this.masterService = masterService;
        this.officialTagService = officialTagService;
    }

    // ---- ファッションの種類・骨格タイプ・パーソナルカラー（kind は fashion-categories / body-types / personal-colors） ----

    @GetMapping("/masters/{kind}")
    public AdminMasterListResponse list(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind) {
        return masterService.list(jwt.getSubject(), kind(kind));
    }

    @PostMapping("/masters/{kind}")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminMasterItem create(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind,
            @RequestBody AdminMasterCreateRequest request) {
        return masterService.create(jwt.getSubject(), kind(kind), request.name());
    }

    @PostMapping("/masters/{kind}/{id:\\d+}/deactivate")
    public AdminMasterItem deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind,
            @PathVariable Long id) {
        return masterService.deactivate(jwt.getSubject(), kind(kind), id);
    }

    @PostMapping("/masters/{kind}/{id:\\d+}/activate")
    public AdminMasterItem activate(@AuthenticationPrincipal Jwt jwt, @PathVariable String kind,
            @PathVariable Long id) {
        return masterService.activate(jwt.getSubject(), kind(kind), id);
    }

    // ---- 公式タグ ----

    @GetMapping("/official-tags")
    public AdminMasterListResponse listOfficialTags(@AuthenticationPrincipal Jwt jwt) {
        return officialTagService.list(jwt.getSubject());
    }

    /** 新しく作ったら 201、同じ名前の手入力のタグを公式にしたら 200（どちらも madeOfficial で区別できる） */
    @PostMapping("/official-tags")
    public ResponseEntity<AdminOfficialTagCreateResponse> createOfficialTag(@AuthenticationPrincipal Jwt jwt,
            @RequestBody AdminMasterCreateRequest request) {
        AdminOfficialTagCreateResponse response = officialTagService.create(jwt.getSubject(), request.name());
        return ResponseEntity.status(response.madeOfficial() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @PostMapping("/official-tags/{id:\\d+}/deactivate")
    public AdminMasterItem deactivateOfficialTag(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return officialTagService.deactivate(jwt.getSubject(), id);
    }

    @PostMapping("/official-tags/{id:\\d+}/activate")
    public AdminMasterItem activateOfficialTag(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return officialTagService.activate(jwt.getSubject(), id);
    }

    private static AdminMasterKind kind(String path) {
        return AdminMasterKind.fromPath(path).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
