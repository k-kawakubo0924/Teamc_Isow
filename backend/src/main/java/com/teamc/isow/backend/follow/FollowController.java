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

    /**
     * フォロー中一覧。sort は newest（フォローの新しい順・既定）か oldest（古い順）。
     * q を指定すると、相手のユーザー名・表示名の一部で絞り込む（画面上部の検索欄）
     */
    @GetMapping("/followings")
    public FollowListResponse followings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return followService.listFollowings(jwt.getSubject(), userId, checkQuery(q), parseSort(sort), page, size);
    }

    /** フォロワー一覧。q・sort はフォロー中一覧と同じ */
    @GetMapping("/followers")
    public FollowListResponse followers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return followService.listFollowers(jwt.getSubject(), userId, checkQuery(q), parseSort(sort), page, size);
    }

    /** ユーザー名・表示名の長さ（50文字）を大きく超える検索語は、一致しないため受け付けない */
    private static String checkQuery(String q) {
        if (q != null && q.length() > MAX_QUERY_LENGTH) {
            throw InputValidationException.of("q", "検索する文字は" + MAX_QUERY_LENGTH + "文字以内で入力してください");
        }
        return q;
    }

    private static final int MAX_QUERY_LENGTH = 50;

    private static FollowSort parseSort(String sort) {
        return FollowSort.fromParam(sort)
                .orElseThrow(() -> InputValidationException.of("sort", "並び順の指定が正しくありません"));
    }
}
