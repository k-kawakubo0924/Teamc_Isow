package com.teamc.isow.backend.search;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.user.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 検索履歴（docs/search.md）。記録は検索 API から呼び、取得・削除は本人の履歴だけを扱う */
@Service
public class SearchHistoryService {

    /** 1ユーザーあたりの保存件数の上限。超えた分は検索日時の古いものから削除する */
    public static final int MAX_PER_USER = 20;

    private final SearchHistoryRepository searchHistoryRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final TransactionTemplate transactionTemplate;

    public SearchHistoryService(
            SearchHistoryRepository searchHistoryRepository,
            UserRepository userRepository,
            AuthService authService,
            PlatformTransactionManager transactionManager) {
        this.searchHistoryRepository = searchHistoryRepository;
        this.userRepository = userRepository;
        this.authService = authService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * キーワードを検索履歴に記録する。
     * キーワードなし（null・空白だけ）の検索は記録しない。同じキーワードがあれば、新しい行は作らずに検索日時を更新する。
     *
     * @param keyword 入力されたキーワード（SearchHistory.MAX_KEYWORD_LENGTH 文字まで。超える場合は呼び出し元で弾くこと）
     */
    public void record(Long userId, String keyword) {
        String key = SearchKeywordNormalizer.key(keyword);
        if (key == null || key.isEmpty()) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(status -> recordInTransaction(userId, keyword, key));
        } catch (DataIntegrityViolationException e) {
            // 同じキーワードの検索がほぼ同時に届くと、両方が「履歴にない」と判断して片方が一意制約違反になる。
            // その場合は先に届いた方が行を作っているため、やり直せば日時の更新になる。
            // それでも失敗する場合（ユーザーが存在しないなど）は、本当の失敗なのでそのまま投げる
            transactionTemplate.executeWithoutResult(status -> recordInTransaction(userId, keyword, key));
        }
    }

    /** 自分の検索履歴を新しい順に返す */
    @Transactional(readOnly = true)
    public SearchHistoryListResponse list(String subject) {
        Long userId = authService.requireCurrentUser(subject).getId();
        return new SearchHistoryListResponse(searchHistoryRepository.findByUserIdOrderBySearchedAtDescIdDesc(userId)
                .stream()
                .map(SearchHistoryListResponse.Item::from)
                .toList());
    }

    /**
     * 自分の検索履歴を1件削除する。他人の履歴・存在しない ID は何もしない
     * （エラーにすると他人の履歴の有無が分かるため。連打で2回届いても成功にするため）
     */
    @Transactional
    public void delete(String subject, Long historyId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        searchHistoryRepository.deleteByIdAndUserId(historyId, userId);
    }

    /** 自分の検索履歴をすべて削除する */
    @Transactional
    public void deleteAll(String subject) {
        Long userId = authService.requireCurrentUser(subject).getId();
        searchHistoryRepository.deleteAllByUserId(userId);
    }

    private void recordInTransaction(Long userId, String keyword, String key) {
        LocalDateTime now = LocalDateTime.now();
        searchHistoryRepository.findByUserIdAndNormalizedKeyword(userId, key).ifPresentOrElse(
                history -> history.searchedAgain(keyword, now),
                () -> {
                    searchHistoryRepository.saveAndFlush(
                            new SearchHistory(userRepository.getReferenceById(userId), keyword, now));
                    deleteOverLimit(userId);
                });
    }

    /**
     * 上限を超えた古い履歴を消す。行を増やしたときだけ呼ぶ（日時の更新では件数は変わらない）。
     * 別のキーワードの検索が同時に届くと一時的に上限を超えることがあるが、次に記録したときに消える
     */
    private void deleteOverLimit(Long userId) {
        List<Long> ids = searchHistoryRepository.findIdsNewestFirst(userId);
        if (ids.size() > MAX_PER_USER) {
            searchHistoryRepository.deleteAllByIdInBatch(ids.subList(MAX_PER_USER, ids.size()));
        }
    }
}
