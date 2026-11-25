package com.teamc.isow.backend.report;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportCaseRepository extends JpaRepository<ReportCase, Long> {

    /** 対象の未対応のまとまり（あれば1つ）。targetType は ReportTargetType の定数名 */
    @Query("SELECT c FROM ReportCase c WHERE c.targetType = :targetType AND c.targetId = :targetId AND c.open = TRUE")
    Optional<ReportCase> findOpen(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    /** 行をロックして読み込む（通報の追加・状態の変更が同時に届いても、1つずつ処理するため） */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM ReportCase c WHERE c.id = :id")
    Optional<ReportCase> findByIdForUpdate(@Param("id") Long id);

    /**
     * 管理画面の一覧：状態・対象の種類で絞り（null なら絞らない）、新しく通報された順（同じなら ID の大きい順）
     */
    @Query("""
            SELECT c FROM ReportCase c
            WHERE (:status IS NULL OR c.status = :status)
              AND (:targetType IS NULL OR c.targetType = :targetType)
            ORDER BY c.lastReportedAt DESC, c.id DESC
            """)
    Slice<ReportCase> findPage(@Param("status") String status, @Param("targetType") String targetType,
            Pageable pageable);

    /** 対応を終えてから保存期間を過ぎ、証拠をまだ消していないまとまりの ID（cutoff 以前に終えたもの） */
    @Query("""
            SELECT c.id FROM ReportCase c
            WHERE c.closedAt IS NOT NULL AND c.closedAt <= :cutoff AND c.evidencePurgedAt IS NULL
            ORDER BY c.id
            """)
    List<Long> findIdsToPurge(@Param("cutoff") LocalDateTime cutoff);
}
