package com.teamc.isow.backend.search;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Long> {

    /** 同じキーワードの履歴（normalizedKeyword は SearchKeywordNormalizer.key で作る） */
    Optional<SearchHistory> findByUserIdAndNormalizedKeyword(Long userId, String normalizedKeyword);

    /** ユーザーの履歴の ID を新しい順に返す（上限を超えた古い履歴を探すのに使う。件数は上限＋数件にとどまる） */
    @Query("""
            SELECT h.id FROM SearchHistory h
            WHERE h.user.id = :userId
            ORDER BY h.searchedAt DESC, h.id DESC
            """)
    List<Long> findIdsNewestFirst(@Param("userId") Long userId);
}
