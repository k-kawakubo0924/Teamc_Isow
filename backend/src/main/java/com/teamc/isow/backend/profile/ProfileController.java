package com.teamc.isow.backend.profile;

import com.teamc.isow.backend.post.PostCardListResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * プロフィール（docs/profile.md）。認証が必要（SecurityConfig の anyRequest）。
 * ユーザー ID は数字のみ（/me は文字どおり一致するパスとして別に扱う）。
 */
@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ProfileResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getMine(jwt.getSubject());
    }

    /** お気に入りは本人にだけ見せるため、他のユーザーのお気に入り一覧の API は用意しない */
    @GetMapping("/me/favorites")
    public PostCardListResponse myFavorites(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return profileService.listMyFavorites(jwt.getSubject(), page, size);
    }

    @GetMapping("/{userId:\\d+}")
    public ProfileResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
        return profileService.get(jwt.getSubject(), userId);
    }

    /** 投稿の新しい順 */
    @GetMapping("/{userId:\\d+}/posts")
    public PostCardListResponse posts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return profileService.listPosts(jwt.getSubject(), userId, page, size);
    }
}
