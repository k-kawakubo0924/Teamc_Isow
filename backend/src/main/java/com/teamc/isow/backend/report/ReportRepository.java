package com.teamc.isow.backend.report;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, Long> {

    /** 同じ人がこのまとまりをすでに通報しているか */
    boolean existsByReportCaseIdAndReporterId(Long caseId, Long reporterId);

    /** reporterId の人が since より後にした通報の数（1人1日の上限） */
    long countByReporterIdAndReportedAtAfter(Long reporterId, LocalDateTime since);

    /** まとまりの通報（通報した人も一緒に読み込む）。古い順 */
    @Query("SELECT r FROM Report r JOIN FETCH r.reporter WHERE r.reportCase.id = :caseId ORDER BY r.reportedAt, r.id")
    List<Report> findByCaseId(@Param("caseId") Long caseId);

    /** 理由ごとの件数（管理画面の一覧の「理由の内訳」。ページ分のまとまりを1本の SQL で数える） */
    @Query("""
            SELECT r.reportCase.id AS caseId, r.reason AS reason, COUNT(r) AS count FROM Report r
            WHERE r.reportCase.id IN :caseIds
            GROUP BY r.reportCase.id, r.reason
            """)
    List<ReasonCount> countReasons(@Param("caseIds") Collection<Long> caseIds);

    interface ReasonCount {
        Long getCaseId();

        String getReason();

        long getCount();
    }
}
