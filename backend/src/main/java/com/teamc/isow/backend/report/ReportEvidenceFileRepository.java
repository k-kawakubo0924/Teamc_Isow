package com.teamc.isow.backend.report;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportEvidenceFileRepository extends JpaRepository<ReportEvidenceFile, Long> {

    /** まとまりの証拠の写真（表示順） */
    List<ReportEvidenceFile> findByReportCaseIdOrderBySortOrder(Long caseId);

    /** まとまりの証拠の写真1枚。別のまとまりの写真は見つからない（ID の組み合わせで別の証拠を取らせない） */
    Optional<ReportEvidenceFile> findByIdAndReportCaseId(Long id, Long caseId);
}
