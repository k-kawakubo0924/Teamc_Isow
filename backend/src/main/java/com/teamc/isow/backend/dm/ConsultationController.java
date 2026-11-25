package com.teamc.isow.backend.dm;

import com.teamc.isow.backend.common.InputValidationException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 相談の申込と、申し込めるかの確認（docs/dm.md）。認証が必要（SecurityConfig の anyRequest） */
@RestController
public class ConsultationController {

    private final ConsultationService consultationService;

    public ConsultationController(ConsultationService consultationService) {
        this.consultationService = consultationService;
    }

    /** 相談を申し込む。会話を「申請中」で作るため 201 を返す */
    @PostMapping("/api/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationResponse request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsultationRequest request) {
        return consultationService.request(jwt.getSubject(), request);
    }

    /** 相手に相談を申し込めるか（プロフィール画面の「相談する」ボタンの状態に使う） */
    @GetMapping("/api/users/{userId:\\d+}/consultation-status")
    public ConsultationStatusResponse status(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return consultationService.status(jwt.getSubject(), userId);
    }

    /**
     * 複数の相手について、申し込めるかをまとめて返す（フォロー中一覧の「相談する」ボタン。1人ずつ呼ぶと人数分の通信になるため）。
     * ids はカンマ区切りで 1〜MAX_STATUS_USERS 人。存在しないユーザーの ID は結果に含めない
     */
    @GetMapping("/api/users/consultation-statuses")
    public ConsultationStatusesResponse statuses(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > ConsultationService.MAX_STATUS_USERS) {
            throw InputValidationException.of(
                    "ids", "ユーザーを1〜" + ConsultationService.MAX_STATUS_USERS + "人指定してください");
        }
        return consultationService.statuses(jwt.getSubject(), ids);
    }
}
