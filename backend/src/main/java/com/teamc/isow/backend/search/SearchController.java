package com.teamc.isow.backend.search;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 検索（docs/search.md）。ログイン後の画面でのみ使うため認証が必要（SecurityConfig の anyRequest） */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;
    private final SearchHistoryService searchHistoryService;

    public SearchController(SearchService searchService, SearchHistoryService searchHistoryService) {
        this.searchService = searchService;
        this.searchHistoryService = searchHistoryService;
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

    /**
     * ユーザーの検索。自分自身は含めない。
     * 並び順はユーザー名か表示名の完全一致 → 前方一致 → 部分一致 → ユーザー名の順
     *
     * @param q キーワード（ユーザー名・表示名の部分一致。100文字まで）。省略すると自分以外の全ユーザー。指定すると検索履歴に記録する
     */
    @GetMapping("/users")
    public UserSearchResponse searchUsers(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return searchService.searchUsers(jwt.getSubject(), q, page, size);
    }

    /** 自分の検索履歴（新しい順。検索項目が選ばれていないときに表示する） */
    @GetMapping("/history")
    public SearchHistoryListResponse history(@AuthenticationPrincipal Jwt jwt) {
        return searchHistoryService.list(jwt.getSubject());
    }

    /** 自分の検索履歴を1件削除する。他人の履歴・存在しない ID でも 204（何も削除しない）。id は数字のみ */
    @DeleteMapping("/history/{id:\\d+}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHistory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        searchHistoryService.delete(jwt.getSubject(), id);
    }

    /** 自分の検索履歴をすべて削除する */
    @DeleteMapping("/history")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAllHistory(@AuthenticationPrincipal Jwt jwt) {
        searchHistoryService.deleteAll(jwt.getSubject());
    }

    /**
     * 検索項目が選ばれたときに出す候補ワード（最大10件）。
     * そのファッションの種類の投稿でよく使われているタグ → 足りなければ公式タグで補う
     *
     * @param categoryId ファッションの種類の ID。省略すると全投稿でよく使われているタグ
     */
    @GetMapping("/suggestions")
    public SuggestionResponse suggestions(
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String categoryId) {
        return searchService.suggestions(jwt.getSubject(), categoryId);
    }
}
