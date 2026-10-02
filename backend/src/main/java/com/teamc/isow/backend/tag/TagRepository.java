package com.teamc.isow.backend.tag;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** TagNameNormalizer.key() で正規化済みの値で渡すこと */
    boolean existsByNormalizedName(String normalizedName);

    /** TagNameNormalizer.key() で正規化済みの値で渡すこと */
    List<Tag> findByNormalizedNameIn(Collection<String> normalizedNames);

    /** 選択肢として出す公式タグ（有効なもの）を、並び順・ID順で返す */
    List<Tag> findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc();

    /**
     * 入力候補の検索。正規化済みの名前の部分一致で、有効なタグ（公式・手入力の両方）を返す。
     * 前方一致するもの → 公式タグ → 名前順に並べる。件数の上限は pageable で指定する。
     * pattern は LIKE 用にエスケープ済み（エスケープ文字は !）であること
     */
    @Query("""
            SELECT t FROM Tag t
            WHERE t.active = true
              AND t.normalizedName LIKE CONCAT('%', :pattern, '%') ESCAPE '!'
            ORDER BY
              CASE WHEN t.normalizedName LIKE CONCAT(:pattern, '%') ESCAPE '!' THEN 0 ELSE 1 END,
              t.official DESC,
              t.normalizedName
            """)
    List<Tag> searchCandidates(@Param("pattern") String pattern, Pageable pageable);
}
