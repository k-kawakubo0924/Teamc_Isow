package com.teamc.isow.backend.report;

import com.teamc.isow.backend.admin.AdminAccess;
import com.teamc.isow.backend.admin.AdminAction;
import com.teamc.isow.backend.admin.AdminOperationLogger;
import com.teamc.isow.backend.admin.AdminTargetType;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.dm.MessageRepository;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 管理者が通報を見る・状態を記録する（docs/admin.md「通報」）。管理者だけが使える（AdminAccess で確かめる）。
 *
 * <ul>
 *   <li><b>措置（投稿の非公開化・削除、ユーザーの利用停止）はまだ作っていない</b>。「対応済み」にしても実際の措置は行われず、状態を記録するだけ</li>
 *   <li>状態の変更は、同じトランザクションで管理操作のログに残す（残せなければ変更も取り消す）。ログの内容は状態だけで、
 *       メモ・通報した人の書いた内容・ユーザー名は書かない（個人を特定する情報になりうるため）</li>
 *   <li>DM の通報の詳細を開いたときは、DM を見たことをログに残す（docs/admin.md「方針」）。残せなければ中身も返さない</li>
 *   <li>証拠の写真は、管理者であることを確かめてから中身を返す（/uploads/** では配信しない）</li>
 * </ul>
 */
@Service
public class AdminReportService {

    private static final Logger log = LoggerFactory.getLogger(AdminReportService.class);

    private final AdminAccess adminAccess;
    private final AdminOperationLogger operationLogger;
    private final ReportCaseRepository caseRepository;
    private final ReportRepository reportRepository;
    private final ReportEvidenceFileRepository evidenceFileRepository;
    private final EvidenceStorage evidenceStorage;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AdminReportService(AdminAccess adminAccess, AdminOperationLogger operationLogger,
            ReportCaseRepository caseRepository, ReportRepository reportRepository,
            ReportEvidenceFileRepository evidenceFileRepository, EvidenceStorage evidenceStorage,
            PostRepository postRepository, UserRepository userRepository, MessageRepository messageRepository,
            JsonMapper jsonMapper, Clock clock) {
        this.adminAccess = adminAccess;
        this.operationLogger = operationLogger;
        this.caseRepository = caseRepository;
        this.reportRepository = reportRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.evidenceStorage = evidenceStorage;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * 一覧（対象ごとに1行、新しく通報された順）。status・targetType は定数名（null・空なら絞らない。status の既定は画面で OPEN）。
     * 理由の内訳はページ分をまとめて1本の SQL で数える
     */
    @Transactional(readOnly = true)
    public AdminReportResponses.ListResponse list(String subject, String status, String targetType, int page, int size) {
        adminAccess.requireAdmin(subject);
        String statusFilter = blankToNull(status);
        String typeFilter = blankToNull(targetType);
        if (statusFilter != null) {
            parse(ReportStatus.class, statusFilter, "status");
        }
        if (typeFilter != null) {
            parse(ReportTargetType.class, typeFilter, "targetType");
        }
        Pageable pageable = PostCardService.pageRequest(page, size);
        Slice<ReportCase> cases = caseRepository.findPage(statusFilter, typeFilter, pageable);
        List<Long> caseIds = cases.getContent().stream().map(ReportCase::getId).toList();
        Map<Long, List<AdminReportResponses.ReasonCount>> reasons = caseIds.isEmpty()
                ? Map.of()
                : reportRepository.countReasons(caseIds).stream().collect(Collectors.groupingBy(
                        ReportRepository.ReasonCount::getCaseId,
                        Collectors.mapping(count -> new AdminReportResponses.ReasonCount(
                                ReportReason.valueOf(count.getReason()), count.getCount()), Collectors.toList())));
        List<AdminReportResponses.ListItem> items = cases.getContent().stream()
                .map(c -> new AdminReportResponses.ListItem(c.getId(), c.getTargetType(), c.getTargetId(), c.getStatus(),
                        c.getSummary(), c.getReportCount(), sortReasons(reasons.getOrDefault(c.getId(), List.of())),
                        c.getFirstReportedAt(), c.getLastReportedAt(), c.isEvidencePurged()))
                .toList();
        return new AdminReportResponses.ListResponse(items, pageable.getPageNumber(), pageable.getPageSize(),
                cases.hasNext());
    }

    /**
     * 詳細。DM の通報で証拠が残っていれば、DM を見たことを同じトランザクションでログに残す（残せなければ中身も返さない）
     */
    @Transactional
    public AdminReportResponses.Detail detail(String subject, Long caseId) {
        User admin = adminAccess.requireAdmin(subject);
        ReportCase reportCase = findCase(caseId);
        EvidenceSnapshot evidence = reportCase.getEvidenceJson() == null
                ? null : jsonMapper.readValue(reportCase.getEvidenceJson(), EvidenceSnapshot.class);
        if (reportCase.getTargetType() == ReportTargetType.MESSAGE && evidence != null) {
            operationLogger.recordByAdmin(admin, AdminAction.REPORT_DM_VIEWED, AdminTargetType.REPORT, caseId,
                    "通報されたメッセージと前後のメッセージ（" + evidence.messages().size() + "件）を表示");
        }
        List<AdminReportResponses.EvidenceFile> files = evidenceFileRepository.findByReportCaseIdOrderBySortOrder(caseId)
                .stream()
                .map(file -> new AdminReportResponses.EvidenceFile(file.getId(), file.getMessageId()))
                .toList();
        List<AdminReportResponses.ReportItem> reports = reportRepository.findByCaseId(caseId).stream()
                .map(r -> new AdminReportResponses.ReportItem(r.getReporter().getId(), r.getReporter().getUsername(),
                        r.getReason(), r.getComment(), r.getReportedAt()))
                .toList();
        return new AdminReportResponses.Detail(reportCase.getId(), reportCase.getTargetType(), reportCase.getTargetId(),
                reportCase.getTargetUserId(),
                reportCase.getStatus(), reportCase.getReportCount(), reportCase.getFirstReportedAt(),
                reportCase.getLastReportedAt(), reportCase.getClosedAt(), reportCase.getHandledByUsername(),
                reportCase.getAdminMemo(), reportCase.isEvidencePurged(), reportCase.getEvidencePurgedAt(), evidence,
                files, currentState(reportCase), reports);
    }

    /**
     * 状態を変える（対応済み・却下・未対応に戻す）。措置は行わない。同じトランザクションでログに残す。
     * すでにその状態なら 409。未対応に戻すのは、同じ対象に新しい未対応の通報がなく、証拠を消していない場合だけ（それ以外は 409）
     */
    @Transactional
    public AdminReportResponses.StatusResult changeStatus(String subject, Long caseId, AdminReportStatusRequest request) {
        User admin = adminAccess.requireAdmin(subject);
        ReportStatus newStatus = parse(ReportStatus.class, request.status(), "status");
        if (request.memo() != null && request.memo().length() > ReportCase.MAX_MEMO_LENGTH) {
            throw InputValidationException.of("memo", "メモは" + ReportCase.MAX_MEMO_LENGTH + "文字以内で入力してください");
        }
        ReportCase reportCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (reportCase.getStatus() == newStatus) {
            throw new AdminReportConflictException("すでに「" + newStatus.getLabel() + "」です。");
        }
        if (newStatus == ReportStatus.OPEN) {
            if (reportCase.isEvidencePurged()) {
                throw new AdminReportConflictException("証拠の保存期間が過ぎたため、未対応に戻せません。");
            }
            if (caseRepository.findOpen(reportCase.getTargetType().name(), reportCase.getTargetId()).isPresent()) {
                throw new AdminReportConflictException("同じ対象に、新しい未対応の通報があります。そちらで対応してください。");
            }
        }
        reportCase.changeStatus(newStatus, admin, request.memo(), LocalDateTime.now(clock));
        caseRepository.flush();
        AdminAction action = switch (newStatus) {
            case RESOLVED -> AdminAction.REPORT_RESOLVED;
            case DISMISSED -> AdminAction.REPORT_DISMISSED;
            case OPEN -> AdminAction.REPORT_REOPENED;
        };
        // ログの内容は状態だけ（メモ・通報した人の書いた内容・ユーザー名は書かない）
        operationLogger.recordByAdmin(admin, action, AdminTargetType.REPORT, caseId, "状態：" + newStatus.getLabel());
        return new AdminReportResponses.StatusResult(reportCase.getId(), reportCase.getStatus(), reportCase.getClosedAt(),
                reportCase.getHandledByUsername(), reportCase.getAdminMemo());
    }

    /**
     * 措置（docs/admin.md「6. 措置」）とあわせて、結びつけた通報を「対応済み」にする。措置と同じトランザクションの中で呼ぶ。
     * 管理者であることは呼ぶ側（AdminModerationService）で確かめていること。未対応でなければ 409。
     * メモは変えない（措置のメモは措置の履歴に残す）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void resolveWithModeration(User admin, ReportCase reportCase) {
        if (reportCase.getStatus() != ReportStatus.OPEN) {
            throw new AdminReportConflictException("この通報はすでに「" + reportCase.getStatus().getLabel() + "」です。");
        }
        reportCase.changeStatus(ReportStatus.RESOLVED, admin, reportCase.getAdminMemo(), LocalDateTime.now(clock));
        caseRepository.flush();
        operationLogger.recordByAdmin(admin, AdminAction.REPORT_RESOLVED, AdminTargetType.REPORT, reportCase.getId(),
                "状態：" + ReportStatus.RESOLVED.getLabel() + "（措置とあわせて）");
    }

    /**
     * 証拠の写真の中身。管理者であることを確かめてから返す。別のまとまりの写真・消した写真は 404
     */
    @Transactional(readOnly = true)
    public EvidenceImage evidenceFile(String subject, Long caseId, Long fileId) {
        adminAccess.requireAdmin(subject);
        ReportEvidenceFile file = evidenceFileRepository.findByIdAndReportCaseId(fileId, caseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            return new EvidenceImage(evidenceStorage.read(file.getFileName()), file.getContentType());
        } catch (RuntimeException e) {
            log.warn("証拠の写真を読めませんでした: caseId={}, fileId={}", caseId, fileId, e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** 証拠の写真の中身と MIME タイプ */
    public record EvidenceImage(byte[] content, String contentType) {
    }

    private ReportCase findCase(Long caseId) {
        return caseRepository.findById(caseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /** 対象の今の状態。投稿は、通報の後に編集されていれば EDITED */
    private AdminReportResponses.CurrentState currentState(ReportCase reportCase) {
        Long targetId = reportCase.getTargetId();
        return switch (reportCase.getTargetType()) {
            case POST -> postRepository.findById(targetId)
                    .map(post -> post.getEditedAt() != null && post.getEditedAt().isAfter(reportCase.getFirstReportedAt())
                            ? AdminReportResponses.CurrentState.EDITED
                            : AdminReportResponses.CurrentState.EXISTS)
                    .orElse(AdminReportResponses.CurrentState.DELETED);
            case USER -> userRepository.existsById(targetId)
                    ? AdminReportResponses.CurrentState.EXISTS : AdminReportResponses.CurrentState.DELETED;
            case MESSAGE -> messageRepository.existsById(targetId)
                    ? AdminReportResponses.CurrentState.EXISTS : AdminReportResponses.CurrentState.DELETED;
        };
    }

    private static List<AdminReportResponses.ReasonCount> sortReasons(List<AdminReportResponses.ReasonCount> reasons) {
        return reasons.stream()
                .sorted(Comparator.comparingLong(AdminReportResponses.ReasonCount::count).reversed()
                        .thenComparing(count -> count.reason().ordinal()))
                .toList();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw InputValidationException.of(field, "値が正しくありません");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
