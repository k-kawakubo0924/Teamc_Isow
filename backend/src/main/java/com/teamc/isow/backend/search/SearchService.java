package com.teamc.isow.backend.search;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.common.SearchPatterns;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.post.PostRepository;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 投稿の検索（docs/search.md）。
 *
 * <p>件数に関係なく決まった本数の SQL で取得する（投稿ごとに SQL を発行する N+1 問題を防ぐ）。
 * 並び順どおりに、そのページの投稿 ID だけを取得し、中身は PostCardService でまとめて読む（ホームの一覧と同じ）。
 *
 * <p>検索履歴の記録は SearchHistoryService が自分のトランザクションで行うため、このクラスではトランザクションを張らない
 * （読み取り専用のトランザクションの中で呼ぶと、履歴を書き込めないため）。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final PostRepository postRepository;
    private final PostCardService postCardService;
    private final FashionCategoryRepository fashionCategoryRepository;
    private final SearchHistoryService searchHistoryService;
    private final AuthService authService;

    public SearchService(
            PostRepository postRepository,
            PostCardService postCardService,
            FashionCategoryRepository fashionCategoryRepository,
            SearchHistoryService searchHistoryService,
            AuthService authService) {
        this.postRepository = postRepository;
        this.postCardService = postCardService;
        this.fashionCategoryRepository = fashionCategoryRepository;
        this.searchHistoryService = searchHistoryService;
        this.authService = authService;
    }

    /**
     * 条件をすべて満たす投稿を返す。null・空の条件は絞り込みに使わない（すべて省略した場合は全投稿）。
     * キーワードがあれば検索履歴に記録する。
     *
     * @param keyword 題名・投稿説明・タグ名の部分一致（大文字小文字・全角半角を区別しない。SearchHistory.MAX_KEYWORD_LENGTH 文字まで）
     * @param categoryId ファッションの種類の ID（数字の文字列）
     * @param ageGroup 投稿者の年代（AgeGroup の定数名）
     * @throws InputValidationException キーワードが長すぎる・存在しない選択肢を指定した場合
     */
    public PostSearchResponse searchPosts(
            String subject, String keyword, String categoryId, String ageGroup, int page, int size) {
        Long userId = authService.requireCurrentUser(subject).getId();

        Map<String, String> errors = new LinkedHashMap<>();
        String displayKeyword = SearchKeywordNormalizer.displayKeyword(keyword);
        boolean hasKeyword = displayKeyword != null && !displayKeyword.isEmpty();
        if (hasKeyword && displayKeyword.length() > SearchHistory.MAX_KEYWORD_LENGTH) {
            errors.put("q", "キーワードは" + SearchHistory.MAX_KEYWORD_LENGTH + "文字以内で入力してください");
        }
        Long fashionCategoryId = parseCategoryId(categoryId, errors);
        AgeGroup parsedAgeGroup = parseAgeGroup(ageGroup, errors);
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        Pageable pageable = PostCardService.pageRequest(page, size);
        String pattern = hasKeyword ? SearchPatterns.contains(SearchKeywordNormalizer.key(displayKeyword)) : null;
        Page<Long> ids = postRepository.searchIds(
                pattern, fashionCategoryId, parsedAgeGroup == null ? null : parsedAgeGroup.name(), pageable);
        PostSearchResponse response = new PostSearchResponse(postCardService.build(userId, ids.getContent()),
                pageable.getPageNumber(), pageable.getPageSize(), ids.hasNext(), ids.getTotalElements());

        if (hasKeyword) {
            recordHistory(userId, displayKeyword);
        }
        return response;
    }

    /** 履歴は補助的な機能のため、記録に失敗しても検索結果は返す */
    private void recordHistory(Long userId, String keyword) {
        try {
            searchHistoryService.record(userId, keyword);
        } catch (RuntimeException e) {
            log.warn("検索履歴を記録できませんでした: userId={}", userId, e);
        }
    }

    /** 存在するファッションの種類の ID だけを受け付ける。非表示にした種類も、その投稿を探せるよう受け付ける */
    private Long parseCategoryId(String value, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Long id = Long.valueOf(value.strip());
            if (fashionCategoryRepository.existsById(id)) {
                return id;
            }
        } catch (NumberFormatException e) {
            // 下でエラーにする
        }
        errors.put("categoryId", "ファッションの種類の選択肢が正しくありません");
        return null;
    }

    private static AgeGroup parseAgeGroup(String value, Map<String, String> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Arrays.stream(AgeGroup.values())
                .filter(ageGroup -> ageGroup.name().equals(value.strip()))
                .findFirst()
                .orElseGet(() -> {
                    errors.put("ageGroup", "年代の選択肢が正しくありません");
                    return null;
                });
    }
}
