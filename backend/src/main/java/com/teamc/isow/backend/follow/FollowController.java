package com.teamc.isow.backend.follow;

import com.teamc.isow.backend.common.InputValidationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * フォローと、フォロー中一覧・フォロワー一覧（docs/profile.md）。認証が必要（SecurityConfig の anyRequest）。
 * 何回呼んでも結果が同じになるため、フォロー（POST）でも 201 ではなく 200 を返す。
 */
@RestController
@RequestMapping("/api/users/{userId:\\d+}")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/follow")
    public FollowResponse follow(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return followService.follow(jwt.getSubject(), userId);
    }

    @DeleteMapping("/follow")
    public FollowResponse unfollow(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return followService.unfollow(jwt.getSubject(), userId);
    }

    /** フォロー中一覧。sort は newest（フォローの新しい順・既定）か oldest（古い順） */
    @GetMapping("/followings")
    public FollowListResponse followings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return followService.listFollowings(jwt.getSubject(), userId, parseSort(sort), page, size);
    }

    /** フォロワー一覧。sort はフォロー中一覧と同じ */
    @GetMapping("/followers")
    public FollowListResponse followers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return followService.listFollowers(jwt.getSubject(), userId, parseSort(sort), page, size);
    }

    private static FollowSort parseSort(String sort) {
        return FollowSort.fromParam(sort)
                .orElseThrow(() -> InputValidationException.of("sort", "並び順の指定が正しくありません"));
    }
}
