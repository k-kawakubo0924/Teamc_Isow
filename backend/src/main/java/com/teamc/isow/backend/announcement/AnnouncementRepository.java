package com.teamc.isow.backend.announcement;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * お知らせの保存と読み込み。発行後の訂正はできず、取り消しも行を消さない（docs/admin.md「お知らせの発行」）ため、
 * 削除のメソッドは持たせない（JpaRepository を継承しない）
 */
public interface AnnouncementRepository extends Repository<Announcement, Long> {

    Announcement save(Announcement announcement);

    /** 行を読み込まずに、外部キーとして使う参照を作る（トランザクションの中で呼ぶ） */
    Announcement getReferenceById(Long id);

    // ---- 管理画面（取り消したものも含む） ----

    /** 新しい順（同じ日時なら ID の大きい順） */
    Slice<Announcement> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);

    /** 行をロックして読み込む（SELECT ... FOR UPDATE）。取り消しが同時に届いても、1つずつ順に処理するために使う */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Announcement a WHERE a.id = :id")
    Optional<Announcement> findByIdForUpdate(@Param("id") Long id);

    // ---- 利用者側（取り消したものは出さない） ----

    /** 取り消していないお知らせか（既読にするときに、存在を確かめるのに使う） */
    boolean existsByIdAndWithdrawnAtIsNull(Long id);

    /**
     * 取り消していないお知らせを新しい順（同じ日時なら ID の大きい順）に、userId の人が開いた日時と一緒に返す
     * （開いていなければ readAt は null）。既読の記録を一緒に読み込むため、件数に関係なく SQL は1本
     */
    @Query("""
            SELECT new com.teamc.isow.backend.announcement.AnnouncementRow(a.id, a.title, a.body, a.publishedAt, r.readAt)
            FROM Announcement a
            LEFT JOIN AnnouncementRead r ON r.announcement = a AND r.id.userId = :userId
            WHERE a.withdrawnAt IS NULL
            ORDER BY a.publishedAt DESC, a.id DESC
            """)
    Slice<AnnouncementRow> findPageForUser(@Param("userId") Long userId, Pageable pageable);

    /**
     * userId の人の未読のお知らせの件数。取り消したもの・since（登録日時）より前に発行されたもの・開いたものは数えない
     */
    @Query("""
            SELECT COUNT(a) FROM Announcement a
            WHERE a.withdrawnAt IS NULL AND a.publishedAt >= :since
              AND NOT EXISTS (
                SELECT 1 FROM AnnouncementRead r WHERE r.announcement = a AND r.id.userId = :userId)
            """)
    long countUnread(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
