package com.teamc.isow.backend.reaction;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 投稿へのいいね・お気に入り。認証が必要（SecurityConfig の anyRequest）。
 * 何回呼んでも結果が同じになるため、登録（POST）でも 201 ではなく 200 を返す。
 */
@RestController
@RequestMapping("/api/posts/{postId:\\d+}")
public class ReactionController {

    private final ReactionService reactionService;

    public ReactionController(ReactionService reactionService) {
        this.reactionService = reactionService;
    }

    @PostMapping("/like")
    public LikeResponse like(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId) {
        return reactionService.like(jwt.getSubject(), postId);
    }

    @DeleteMapping("/like")
    public LikeResponse unlike(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId) {
        return reactionService.unlike(jwt.getSubject(), postId);
    }

    @PostMapping("/favorite")
    public FavoriteResponse favorite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId) {
        return reactionService.favorite(jwt.getSubject(), postId);
    }

    @DeleteMapping("/favorite")
    public FavoriteResponse unfavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable Long postId) {
        return reactionService.unfavorite(jwt.getSubject(), postId);
    }
}
