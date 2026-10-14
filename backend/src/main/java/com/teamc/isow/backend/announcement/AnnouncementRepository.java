package com.teamc.isow.backend.announcement;

import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * お知らせの保存と読み込み。発行後の訂正・取り消しは未確定のため（docs/admin.md「未確定・要確認」）、
 * 削除のメソッドは持たせない（JpaRepository を継承しない）
 */
public interface AnnouncementRepository extends Repository<Announcement, Long> {

    Announcement save(Announcement announcement);

    /** 行を読み込まずに、外部キーとして使う参照を作る（トランザクションの中で呼ぶ） */
    Announcement getReferenceById(Long id);

    // ---- 管理画面 ----

    /** 新しい順（同じ日時なら ID の大きい順） */
    Slice<Announcement> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);

    // ---- 利用者側 ----

    /** お知らせがあるか（既読にするときに、存在を確かめるのに使う） */
    boolean existsById(Long id);

    /**
     * お知らせを新しい順（同じ日時なら ID の大きい順）に、userId の人が開いた日時と一緒に返す
     * （開いていなければ readAt は null）。既読の記録を一緒に読み込むため、件数に関係なく SQL は1本
     */
    @Query("""
            SELECT new com.teamc.isow.backend.announcement.AnnouncementRow(a.id, a.title, a.body, a.publishedAt, r.readAt)
            FROM Announcement a
            LEFT JOIN AnnouncementRead r ON r.announcement = a AND r.id.userId = :userId
            ORDER BY a.publishedAt DESC, a.id DESC
            """)
    Slice<AnnouncementRow> findPageForUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * userId の人の未読のお知らせの件数。since（登録日時）より前に発行されたもの・開いたものは数えない
     */
    @Query("""
            SELECT COUNT(a) FROM Announcement a
            WHERE a.publishedAt >= :since
              AND NOT EXISTS (
                SELECT 1 FROM AnnouncementRead r WHERE r.announcement = a AND r.id.userId = :userId)
            """)
    long countUnread(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
