package com.teamc.isow.backend.block;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ブロック（docs/profile.md「ブロック」）。ログイン後の画面でのみ使うため認証が必要（SecurityConfig の anyRequest）
 */
@RestController
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    /** ブロックする（204）。すでにブロックしていても 204 */
    @PostMapping("/api/users/{userId:\\d+}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void block(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        blockService.block(jwt.getSubject(), userId);
    }

    /** ブロックを解除する（204）。ブロックしていなくても 204 */
    @DeleteMapping("/api/users/{userId:\\d+}/block")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        blockService.unblock(jwt.getSubject(), userId);
    }

    /** 自分がブロックしている人の一覧（ブロックした新しい順） */
    @GetMapping("/api/blocks")
    public BlockListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return blockService.list(jwt.getSubject(), page, size);
    }
}
