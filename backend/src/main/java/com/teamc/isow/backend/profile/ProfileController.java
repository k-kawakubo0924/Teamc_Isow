package com.teamc.isow.backend.profile;

import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.post.PostCardListResponse;
import com.teamc.isow.backend.post.PostRepository;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * プロフィール（docs/profile.md）。認証が必要（SecurityConfig の anyRequest）。
 * ユーザー ID は数字のみ（/me は文字どおり一致するパスとして別に扱う）。
 */
@RestController
@RequestMapping("/api/users")
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileEditService profileEditService;

    public ProfileController(ProfileService profileService, ProfileEditService profileEditService) {
        this.profileService = profileService;
        this.profileEditService = profileEditService;
    }

    @GetMapping("/me")
    public ProfileResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getMine(jwt.getSubject());
    }

    /** プロフィール編集の「保存」。プロフィール画像以外の項目をまとめて置き換え、更新後のプロフィールを返す */
    @PutMapping("/me")
    public ProfileResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProfileUpdateRequest request) {
        return profileEditService.update(jwt.getSubject(), request);
    }

    /**
     * プロフィール画像の変更（multipart/form-data の image）。更新後のプロフィールを返す。
     * 未選択の場合も画像の検証と同じ 400 にするため、image は必須にしない
     */
    @PostMapping(path = "/me/profile-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ProfileResponse changeImage(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(name = "image", required = false) MultipartFile image) {
        return profileEditService.changeImage(jwt.getSubject(), image);
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

    /**
     * そのユーザーがフォローしている人の投稿（ランダムな順）。
     * seed は画面を開いたときに 1〜2147483646 の乱数で決め、同じ一覧の次のページでも同じ値を送る
     */
    @GetMapping("/{userId:\\d+}/following-posts")
    public PostCardListResponse followingPosts(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @RequestParam long seed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (seed < 1 || seed >= PostRepository.SHUFFLE_MODULUS) {
            throw InputValidationException.of("seed", "並び順の指定が正しくありません");
        }
        return profileService.listFollowingPosts(jwt.getSubject(), userId, seed, page, size);
    }
}
