package com.teamc.isow.backend.account;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, Long> {

    /** userId が since より後に申し込んだ数（1時間に3回までの制限） */
    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime since);

    /** userId が since より後に申し込んだもののうち、最も古いもの（制限にかかったとき、もう一度申し込めるまでの残り時間を計算する） */
    Optional<EmailChangeRequest> findFirstByUserIdAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, LocalDateTime since);

    /** userId の変更待ちの申し込み（新しく申し込んだときに無効にする） */
    @Query("""
            SELECT r FROM EmailChangeRequest r
            WHERE r.user.id = :userId AND r.newEmail IS NOT NULL AND r.completedAt IS NULL AND r.supersededAt IS NULL
            """)
    List<EmailChangeRequest> findPending(@Param("userId") Long userId);
}
