package com.teamc.isow.backend.post;

import com.teamc.isow.backend.common.InputValidationException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 投稿。ログイン後の画面でのみ使うため認証が必要（SecurityConfig の anyRequest） */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final TimelineService timelineService;

    public PostController(PostService postService, TimelineService timelineService) {
        this.postService = postService;
        this.timelineService = timelineService;
    }

    /** 画像ファイルと投稿内容を multipart/form-data で一度に受け取る。投稿者はログイン中のユーザー */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @ModelAttribute PostCreateRequest request) {
        return postService.create(jwt.getSubject(), request);
    }

    /**
     * ホームの投稿一覧（docs/home.md）。tab は recommended（おすすめ・既定）・following（フォロー中）・latest（新着）。
     * ページ番号で区切るため、読み込みの途中で投稿やいいねが増えると、同じ投稿が次のページにも出ることがある。
     * 画面側で id が重複したものを省くこと
     */
    @GetMapping
    public TimelineResponse timeline(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "recommended") String tab,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        TimelineTab timelineTab = TimelineTab.fromParam(tab)
                .orElseThrow(() -> InputValidationException.of("tab", "表示するタブの指定が正しくありません"));
        return timelineService.list(jwt.getSubject(), timelineTab, page, size);
    }

    /** 自分の投稿一覧（新しい順）。/{id} より優先される（文字どおり一致するパスが優先） */
    @GetMapping("/me")
    public PostListResponse myPosts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return postService.listMine(jwt.getSubject(), page, size);
    }

    /** 投稿1件の詳細。id は数字のみ（それ以外は URL が一致せず 404） */
    @GetMapping("/{id:\\d+}")
    public PostResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return postService.get(jwt.getSubject(), id);
    }
}
