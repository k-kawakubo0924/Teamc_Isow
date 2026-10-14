package com.teamc.isow.backend.report;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.block.BlockRepository;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.dm.Message;
import com.teamc.isow.backend.dm.MessageRepository;
import com.teamc.isow.backend.image.ImageStorage;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostImage;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.Tag;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 利用者の通報（docs/admin.md「通報」）。投稿・ユーザー・DM のメッセージを通報できる。
 *
 * <ul>
 *   <li>同じ対象への通報は、未対応の間は1つのまとまり（ReportCase）にまとめる。同じ人の2回目は ALREADY_REPORTED で何も足さない</li>
 *   <li>まとまりを新しく作るとき（最初の通報）に、通報した時点の内容をコピーする（証拠）。
 *       文字は JSON で、写真はすでに保存されているファイルをそのまま証拠の保存先に複製する（prepare() は通さない。CLAUDE.md）</li>
 *   <li>DM は、通報したメッセージと前後5件ずつ（最大11件）だけをコピーする（それ以上は管理者にも見せない。docs/admin.md「方針」）</li>
 *   <li>通報しても、通報された人には何も伝えない（通知を作らない）。自動でブロックもしない</li>
 * </ul>
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    /** 1人が1日（24時間）にできる通報の数（大量の通報で証拠のコピーが溜まらないように） */
    static final int MAX_REPORTS_PER_DAY = 30;

    /** DM の通報で、通報したメッセージの前と後にコピーする件数（それぞれ） */
    static final int MESSAGE_CONTEXT = 5;

    private final ReportCaseRepository caseRepository;
    private final ReportRepository reportRepository;
    private final ReportEvidenceFileRepository evidenceFileRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final BlockRepository blockRepository;
    private final ImageStorage imageStorage;
    private final EvidenceStorage evidenceStorage;
    private final AuthService authService;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ReportService(ReportCaseRepository caseRepository, ReportRepository reportRepository,
            ReportEvidenceFileRepository evidenceFileRepository, PostRepository postRepository,
            UserRepository userRepository, MessageRepository messageRepository, BlockRepository blockRepository,
            ImageStorage imageStorage, EvidenceStorage evidenceStorage, AuthService authService, JsonMapper jsonMapper,
            Clock clock, PlatformTransactionManager transactionManager) {
        this.caseRepository = caseRepository;
        this.reportRepository = reportRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.blockRepository = blockRepository;
        this.imageStorage = imageStorage;
        this.evidenceStorage = evidenceStorage;
        this.authService = authService;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * 通報する。
     *
     * @throws InputValidationException 種類・理由・詳しい内容の誤り、自分自身・自分のものの通報（400）
     * @throws ReportLimitExceededException 1人1日の上限を超えた（429）
     * @throws ReportTargetNotFoundException 対象が存在しない・見えない・当事者でない会話のメッセージ（404）
     */
    public ReportResponse report(String subject, ReportRequest request) {
        Long reporterId = authService.requireCurrentUser(subject).getId();
        ReportTargetType targetType = parseTargetType(request.targetType());
        ReportReason reason = validateReason(targetType, request.reason(), request.comment());
        LocalDateTime now = LocalDateTime.now(clock);
        if (reportRepository.countByReporterIdAndReportedAtAfter(reporterId, now.minusDays(1)) >= MAX_REPORTS_PER_DAY) {
            throw new ReportLimitExceededException();
        }

        // 複製した証拠のファイル名。DB の登録に失敗したら消す（コミットされなかった証拠を残さない）
        List<String> copied = new ArrayList<>();
        try {
            return transactionTemplate.execute(status ->
                    reportInTransaction(reporterId, targetType, request.targetId(), reason, request.comment(), now, copied));
        } catch (DataIntegrityViolationException e) {
            // 同じ対象への最初の通報がほぼ同時に届くと、片方が一意制約違反になる。やり直すと、相手が作ったまとまりに加わる
            deleteCopiesQuietly(copied);
            copied.clear();
            log.info("通報の登録で一意制約違反が発生したため、やり直します: {}", e.getMostSpecificCause().getMessage());
            try {
                return transactionTemplate.execute(status -> reportInTransaction(
                        reporterId, targetType, request.targetId(), reason, request.comment(), now, copied));
            } catch (RuntimeException retryFailure) {
                deleteCopiesQuietly(copied);
                throw retryFailure;
            }
        } catch (RuntimeException e) {
            deleteCopiesQuietly(copied);
            throw e;
        }
    }

    /** トランザクションの中で呼ぶ */
    private ReportResponse reportInTransaction(Long reporterId, ReportTargetType targetType, Long targetId,
            ReportReason reason, String comment, LocalDateTime now, List<String> copied) {
        Target target = findTarget(reporterId, targetType, targetId);
        User reporter = userRepository.getReferenceById(reporterId);

        Optional<ReportCase> open = caseRepository.findOpen(targetType.name(), targetId);
        if (open.isPresent()) {
            ReportCase reportCase = caseRepository.findByIdForUpdate(open.get().getId()).orElseThrow();
            if (reportRepository.existsByReportCaseIdAndReporterId(reportCase.getId(), reporterId)) {
                return new ReportResponse(ReportResponse.Status.ALREADY_REPORTED);
            }
            reportCase.addReport(now);
            reportRepository.save(new Report(reportCase, reporter, reason, comment, now));
            return new ReportResponse(ReportResponse.Status.ACCEPTED);
        }

        ReportCase reportCase = caseRepository.saveAndFlush(new ReportCase(targetType, targetId, target.ownerId(),
                target.summary(), jsonMapper.writeValueAsString(target.snapshot()), now));
        copyEvidenceFiles(reportCase, target.images(), copied);
        reportCase.addReport(now);
        reportRepository.save(new Report(reportCase, reporter, reason, comment, now));
        return new ReportResponse(ReportResponse.Status.ACCEPTED);
    }

    /**
     * 写真を証拠の保存先に複製する。すでに prepare() を通して保存されているファイルを、中身を変えずにそのまま複製する。
     * 読めなかった写真は飛ばして通報は受け付ける（警告のログに残す）
     */
    private void copyEvidenceFiles(ReportCase reportCase, List<SourceImage> images, List<String> copied) {
        int order = 1;
        for (SourceImage image : images) {
            String extension = extensionOf(image.url());
            if (extension == null) {
                log.warn("証拠にする画像の形式が分かりません（コピーしません）: caseId={}", reportCase.getId());
                continue;
            }
            byte[] content;
            try {
                content = imageStorage.read(image.url());
            } catch (RuntimeException e) {
                log.warn("証拠にする画像を読めませんでした（コピーしません）: caseId={}", reportCase.getId(), e);
                continue;
            }
            String fileName = evidenceStorage.storeCopy(content, extension);
            copied.add(fileName);
            evidenceFileRepository.save(new ReportEvidenceFile(reportCase, order++, fileName,
                    "png".equals(extension) ? "image/png" : "image/jpeg", image.messageId()));
        }
    }

    // ---- 対象を探して、通報できるか確かめる ----

    private Target findTarget(Long reporterId, ReportTargetType targetType, Long targetId) {
        return switch (targetType) {
            case POST -> postTarget(reporterId, targetId);
            case USER -> userTarget(reporterId, targetId);
            case MESSAGE -> messageTarget(reporterId, targetId);
        };
    }

    private Target postTarget(Long reporterId, Long postId) {
        Post post = postRepository.findDetailById(postId).orElseThrow(ReportTargetNotFoundException::new);
        User author = post.getAuthor();
        if (author.getId().equals(reporterId)) {
            throw InputValidationException.of("targetId", "自分の投稿は通報できません");
        }
        requireNotBlockedBy(author.getId(), reporterId);
        EvidenceSnapshot.PostEvidence evidence = new EvidenceSnapshot.PostEvidence(post.getId(), author.getId(),
                author.getUsername(), post.getTitle(), post.getFashionCategory().getName(),
                post.getTags().stream().sorted(Comparator.comparing(Tag::getId)).map(Tag::getName).toList(),
                post.getWornItems(), post.getDescription(), post.getReferenceUrl(), post.getCreatedAt(),
                post.getEditedAt());
        List<SourceImage> images = post.getImages().stream()
                .map(PostImage::getImageUrl)
                .map(url -> new SourceImage(url, null))
                .toList();
        return new Target(author.getId(), post.getTitle(), new EvidenceSnapshot(evidence, null, null), images);
    }

    private Target userTarget(Long reporterId, Long userId) {
        User user = userRepository.findById(userId).orElseThrow(ReportTargetNotFoundException::new);
        if (user.getId().equals(reporterId)) {
            throw InputValidationException.of("targetId", "自分自身は通報できません");
        }
        requireNotBlockedBy(user.getId(), reporterId);
        EvidenceSnapshot.UserEvidence evidence =
                new EvidenceSnapshot.UserEvidence(user.getId(), user.getUsername(), user.getDisplayName());
        List<SourceImage> images = user.getProfileImageUrl() == null
                ? List.of() : List.of(new SourceImage(user.getProfileImageUrl(), null));
        return new Target(user.getId(), "@" + user.getUsername(), new EvidenceSnapshot(null, evidence, null), images);
    }

    /**
     * DM のメッセージ。通報できるのは会話の当事者で、相手のメッセージだけ。当事者でなければ存在しないものと同じ 404。
     * ブロックの間柄でも、過去の会話は両方から読めるため通報できる（docs/dm.md「ブロック」）
     */
    private Target messageTarget(Long reporterId, Long messageId) {
        Message message = messageRepository.findById(messageId).orElseThrow(ReportTargetNotFoundException::new);
        if (!message.getConversation().isParticipant(reporterId)) {
            throw new ReportTargetNotFoundException();
        }
        User sender = message.getSender();
        if (sender.getId().equals(reporterId)) {
            throw InputValidationException.of("targetId", "自分のメッセージは通報できません");
        }
        Long conversationId = message.getConversation().getId();
        List<Message> window = new ArrayList<>(
                messageRepository.findLatest(conversationId, messageId, PageRequest.of(0, MESSAGE_CONTEXT)));
        window.sort(Comparator.comparing(Message::getId));
        window.add(message);
        window.addAll(messageRepository.findAfter(conversationId, messageId, PageRequest.of(0, MESSAGE_CONTEXT)));

        List<EvidenceSnapshot.MessageEvidence> messages = new ArrayList<>();
        List<SourceImage> images = new ArrayList<>();
        for (Message m : window) {
            messages.add(new EvidenceSnapshot.MessageEvidence(m.getId(), m.getSender().getId(),
                    m.getSender().getUsername(), m.getBody(), m.getImageUrl() != null, m.getSentAt(),
                    m.getId().equals(messageId)));
            if (m.getImageUrl() != null) {
                images.add(new SourceImage(m.getImageUrl(), m.getId()));
            }
        }
        return new Target(sender.getId(), "@" + sender.getUsername() + " のメッセージ",
                new EvidenceSnapshot(null, null, messages), images);
    }

    /**
     * 対象の持ち主が通報する人をブロックしていれば（通報する人はブロックしていなければ）、存在しないものと同じ 404。
     * 自分がブロックした相手は通報できる（ブロックと通報は一緒に行われることが多いため）
     */
    private void requireNotBlockedBy(Long ownerId, Long reporterId) {
        if (blockRepository.existsByBlockerIdAndBlockedId(ownerId, reporterId)
                && !blockRepository.existsByBlockerIdAndBlockedId(reporterId, ownerId)) {
            throw new ReportTargetNotFoundException();
        }
    }

    // ---- 入力の確認 ----

    private static ReportTargetType parseTargetType(String value) {
        try {
            return ReportTargetType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw InputValidationException.of("targetType", "通報する対象の種類が正しくありません");
        }
    }

    private static ReportReason validateReason(ReportTargetType targetType, String reasonValue, String comment) {
        Map<String, String> errors = new LinkedHashMap<>();
        ReportReason reason = null;
        try {
            reason = ReportReason.valueOf(reasonValue);
            if (!reason.appliesTo(targetType)) {
                errors.put("reason", "この対象には選べない理由です");
            }
        } catch (IllegalArgumentException e) {
            errors.put("reason", "理由を選択してください");
        }
        if (reason == ReportReason.OTHER && comment == null) {
            errors.put("comment", "「その他」を選んだときは、詳しい内容を入力してください");
        }
        if (comment != null && comment.length() > Report.MAX_COMMENT_LENGTH) {
            errors.put("comment", "詳しい内容は" + Report.MAX_COMMENT_LENGTH + "文字以内で入力してください");
        }
        if (!errors.isEmpty()) {
            throw new InputValidationException(errors);
        }
        return reason;
    }

    /** URL の拡張子（jpg・png）。それ以外は null */
    private static String extensionOf(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "jpg";
        }
        return null;
    }

    private void deleteCopiesQuietly(List<String> fileNames) {
        for (String fileName : fileNames) {
            try {
                evidenceStorage.delete(fileName);
            } catch (RuntimeException e) {
                log.warn("登録できなかった通報の証拠のファイルを削除できませんでした（手動で削除が必要）: {}", fileName, e);
            }
        }
    }

    /** 通報する対象（持ち主・一覧の要約・文字のコピー・複製する写真） */
    private record Target(Long ownerId, String summary, EvidenceSnapshot snapshot, List<SourceImage> images) {
    }

    /** 複製する写真（保存先の URL と、DM の画像ならメッセージの ID） */
    private record SourceImage(String url, Long messageId) {
    }
}
