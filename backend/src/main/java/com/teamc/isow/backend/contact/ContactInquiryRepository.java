package com.teamc.isow.backend.contact;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContactInquiryRepository extends JpaRepository<ContactInquiry, Long> {

    /** 1人が since より後に送った数（普通のお問い合わせと申し立てを合わせて数える） */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** 行をロックして読み込む（状態の変更が同時に届いても、1つずつ処理するため） */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ContactInquiry c WHERE c.id = :id")
    Optional<ContactInquiry> findByIdForUpdate(@Param("id") Long id);

    /** 管理画面の一覧：状態・種類・受付の経路で絞り（null なら絞らない）、新しい順（同じなら ID の大きい順） */
    @Query("""
            SELECT c FROM ContactInquiry c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:category IS NULL OR c.category = :category)
              AND (:channel IS NULL OR c.channel = :channel)
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    Slice<ContactInquiry> findPage(@Param("status") String status, @Param("category") String category,
            @Param("channel") String channel, Pageable pageable);
}
