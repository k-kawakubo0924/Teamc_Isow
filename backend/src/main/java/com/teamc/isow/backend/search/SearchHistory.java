package com.teamc.isow.backend.search;

import com.teamc.isow.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * 検索履歴（docs/search.md）。検索項目が選ばれていないときに、ログイン者本人にだけ表示する。
 *
 * <p>キーワードを入力した検索だけを記録する（条件だけの検索は記録しない）。
 * 同じキーワードで再度検索した場合は、新しい行を作らずにこの行の検索日時を更新する（SearchHistoryService を参照）。
 * 1ユーザーあたり SearchHistoryService.MAX_PER_USER 件までで、超えた分は古いものから削除する。
 */
@Entity
@Table(
        name = "search_histories",
        // 同じユーザーの履歴に同じキーワードが並ばないようにする
        uniqueConstraints = @UniqueConstraint(
                name = "uk_search_histories_user_keyword", columnNames = {"user_id", "normalized_keyword"}),
        // 履歴を新しい順に並べるときと、上限を超えた古い履歴を消すときに使う
        indexes = @Index(name = "idx_search_histories_user_searched_at", columnList = "user_id, searched_at"))
public class SearchHistory {

    /** キーワードの最大文字数（整えた後の文字数） */
    public static final int MAX_KEYWORD_LENGTH = 100;

    /** 検索履歴を一意に識別するID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 検索したユーザー（本人以外には見せない） */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 検索したキーワード（画面に表示する形）。同じキーワードで再度検索したら、最後に入力した形に置き換える */
    @Column(name = "keyword", nullable = false, length = MAX_KEYWORD_LENGTH)
    private String keyword;

    /** 同じキーワードかどうかの判定に使う値（keyword を小文字にそろえたもの。SearchKeywordNormalizer.key） */
    @Column(name = "normalized_keyword", nullable = false, length = MAX_KEYWORD_LENGTH)
    private String normalizedKeyword;

    /** 最後に検索した日時（履歴を新しい順に並べるのと、上限を超えたときに古いものを消すのに使う） */
    @Column(name = "searched_at", nullable = false)
    private LocalDateTime searchedAt;

    protected SearchHistory() {
        // JPA 用
    }

    /**
     * @param keyword 入力されたキーワード。整えた後に空になるもの（キーワードなしの検索）は記録しないため受け付けない
     */
    public SearchHistory(User user, String keyword, LocalDateTime searchedAt) {
        this.user = user;
        setKeyword(keyword);
        this.searchedAt = searchedAt;
    }

    /** 同じキーワードで再度検索したときに呼ぶ。検索日時を更新し、表示する形を最後に入力したものに置き換える */
    public void searchedAgain(String keyword, LocalDateTime searchedAt) {
        setKeyword(keyword);
        this.searchedAt = searchedAt;
    }

    private void setKeyword(String keyword) {
        String displayKeyword = SearchKeywordNormalizer.displayKeyword(keyword);
        if (displayKeyword == null || displayKeyword.isEmpty()) {
            throw new IllegalArgumentException("キーワードなしの検索は履歴に記録しない");
        }
        if (displayKeyword.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException("キーワードは" + MAX_KEYWORD_LENGTH + "文字まで");
        }
        this.keyword = displayKeyword;
        this.normalizedKeyword = SearchKeywordNormalizer.key(displayKeyword);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getKeyword() {
        return keyword;
    }

    public String getNormalizedKeyword() {
        return normalizedKeyword;
    }

    public LocalDateTime getSearchedAt() {
        return searchedAt;
    }
}
