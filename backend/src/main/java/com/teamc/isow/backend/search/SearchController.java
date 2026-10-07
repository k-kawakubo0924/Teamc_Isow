package com.teamc.isow.backend.search;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 検索（docs/search.md）。ログイン後の画面でのみ使うため認証が必要（SecurityConfig の anyRequest） */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 投稿の検索。条件はすべて省略でき、指定したものをすべて満たす投稿を返す。
     * 並び順はいいね数の多い順 → 新しい順 → ID の大きい順。
     * ページ番号で区切るため、読み込みの途中でいいねが増えると、同じ投稿が次のページにも出ることがある。
     * 画面側で id が重複したものを省くこと
     *
     * @param q キーワード（題名・投稿説明・タグ名の部分一致。100文字まで）。指定すると検索履歴に記録する
     * @param categoryId ファッションの種類の ID（GET /api/masters の fashionCategories）
     * @param ageGroup 投稿者の年代（GET /api/masters の ageGroups の code）
     */
    @GetMapping("/posts")
    public PostSearchResponse searchPosts(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String ageGroup,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchService.searchPosts(jwt.getSubject(), q, categoryId, ageGroup, page, size);
    }
}
