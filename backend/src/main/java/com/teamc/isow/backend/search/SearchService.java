package com.teamc.isow.backend.search;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.common.SearchPatterns;
import com.teamc.isow.backend.follow.FollowRepository;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * 投稿・ユーザーの検索と候補ワード（docs/search.md）。
 *
 * <p>件数に関係なく決まった本数の SQL で取得する（1件ごとに SQL を発行する N+1 問題を防ぐ）。
 * 投稿は並び順どおりに、そのページの投稿 ID だけを取得し、中身は PostCardService でまとめて読む（ホームの一覧と同じ）。
 * ユーザーは1本の SQL で取得し、フォロー済みかどうかはページ分をまとめて1本で調べる。
 *
 * <p>検索履歴の記録は SearchHistoryService が自分のトランザクションで行うため、このクラスではトランザクションを張らない
 * （読み取り専用のトランザクションの中で呼ぶと、履歴を書き込めないため）。
 */
@Service
public class SearchService {

    /** 候補ワードの件数の上限 */
    public static final int MAX_SUGGESTIONS = 10;

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final PostRepository postRepository;
    private final PostCardService postCardService;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final TagRepository tagRepository;
    private final FashionCategoryRepository fashionCategoryRepository;
    private final SearchHistoryService searchHistoryService;
    private final AuthService authService;

    public SearchService(
            PostRepository postRepository,
            PostCardService postCardService,
            UserRepository userRepository,
            FollowRepository followRepository,
            TagRepository tagRepository,
            FashionCategoryRepository fashionCategoryRepository,
            SearchHistoryService searchHistoryService,
            AuthService authService) {
        this.postRepository = postRepository;
        this.postCardService = postCardService;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.tagRepository = tagRepository;
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
        String displayKeyword = validateKeyword(keyword, errors);
        Long fashionCategoryId = parseCategoryId(categoryId, errors);
        AgeGroup parsedAgeGroup = parseAgeGroup(ageGroup, errors);
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        Pageable pageable = PostCardService.pageRequest(page, size);
        String pattern = displayKeyword == null
                ? null : SearchPatterns.contains(SearchKeywordNormalizer.key(displayKeyword));
        Page<Long> ids = postRepository.searchIds(
                pattern, fashionCategoryId, parsedAgeGroup == null ? null : parsedAgeGroup.name(), pageable);
        PostSearchResponse response = new PostSearchResponse(postCardService.build(userId, ids.getContent()),
                pageable.getPageNumber(), pageable.getPageSize(), ids.hasNext(), ids.getTotalElements());

        recordHistory(userId, displayKeyword);
        return response;
    }

    /**
     * ユーザー名・表示名の部分一致でユーザーを探す（フォロー一覧の検索と同じ SearchPatterns で、大文字小文字を区別しない）。
     * 自分自身は含めない。キーワードがなければ自分以外の全ユーザー。キーワードがあれば検索履歴に記録する。
     *
     * @throws InputValidationException キーワードが長すぎる場合
     */
    public UserSearchResponse searchUsers(String subject, String keyword, int page, int size) {
        Long viewerId = authService.requireCurrentUser(subject).getId();

        Map<String, String> errors = new LinkedHashMap<>();
        String displayKeyword = validateKeyword(keyword, errors);
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        Pageable pageable = PostCardService.pageRequest(page, size);
        Page<User> users = userRepository.search(viewerId,
                keyword == null || keyword.isBlank() ? null : SearchPatterns.contains(keyword),
                SearchPatterns.exact(keyword), SearchPatterns.startsWith(keyword), pageable);
        Set<Long> followingIds = users.isEmpty()
                ? Set.of()
                : new HashSet<>(followRepository.findFollowingIds(
                        viewerId, users.getContent().stream().map(User::getId).toList()));
        List<UserSearchResponse.Item> items = users.getContent().stream()
                .map(user -> new UserSearchResponse.Item(user.getId(), user.getUsername(), user.getDisplayName(),
                        user.getProfileImageUrl(), user.getHeightCm(), user.getGender(),
                        followingIds.contains(user.getId())))
                .toList();
        UserSearchResponse response = new UserSearchResponse(items,
                pageable.getPageNumber(), pageable.getPageSize(), users.hasNext(), users.getTotalElements());

        recordHistory(viewerId, displayKeyword);
        return response;
    }

    /**
     * 検索項目が選ばれたときに出す候補ワード（最大 MAX_SUGGESTIONS 件）。
     * そのファッションの種類の投稿でよく使われているタグを、使われた投稿数の多い順に返す。
     * 投稿が少なく件数に満たない場合は、公式タグ（表示順）で補う。投稿が1件もなくても空にはならない。
     *
     * @param categoryId ファッションの種類の ID（数字の文字列）。null・空なら全投稿で数える
     * @throws InputValidationException 存在しない選択肢を指定した場合
     */
    public SuggestionResponse suggestions(String subject, String categoryId) {
        authService.requireCurrentUser(subject);

        Map<String, String> errors = new LinkedHashMap<>();
        Long fashionCategoryId = parseCategoryId(categoryId, errors);
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }

        List<SuggestionResponse.Item> words = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (String name : tagRepository.findPopularNames(fashionCategoryId, PageRequest.of(0, MAX_SUGGESTIONS))) {
            words.add(new SuggestionResponse.Item(name, SuggestionResponse.Source.POPULAR));
            names.add(name);
        }
        if (words.size() < MAX_SUGGESTIONS) {
            for (Tag tag : tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc()) {
                if (words.size() >= MAX_SUGGESTIONS) {
                    break;
                }
                if (names.add(tag.getName())) {
                    words.add(new SuggestionResponse.Item(tag.getName(), SuggestionResponse.Source.OFFICIAL));
                }
            }
        }
        return new SuggestionResponse(words);
    }

    /**
     * キーワードを整えて長さを確かめる。キーワードがなければ null を返す。
     * 長すぎる場合は errors に追加する（検索履歴に保存できる長さと同じ上限）
     */
    private static String validateKeyword(String keyword, Map<String, String> errors) {
        String displayKeyword = SearchKeywordNormalizer.displayKeyword(keyword);
        if (displayKeyword == null || displayKeyword.isEmpty()) {
            return null;
        }
        if (displayKeyword.length() > SearchHistory.MAX_KEYWORD_LENGTH) {
            errors.put("q", "キーワードは" + SearchHistory.MAX_KEYWORD_LENGTH + "文字以内で入力してください");
        }
        return displayKeyword;
    }

    /** キーワードがあれば記録する。履歴は補助的な機能のため、記録に失敗しても検索結果は返す */
    private void recordHistory(Long userId, String keyword) {
        if (keyword == null) {
            return;
        }
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
