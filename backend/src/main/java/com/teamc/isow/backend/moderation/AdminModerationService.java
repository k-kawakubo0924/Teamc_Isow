package com.teamc.isow.backend.moderation;

import com.teamc.isow.backend.admin.AdminAccess;
import com.teamc.isow.backend.admin.AdminAction;
import com.teamc.isow.backend.admin.AdminOperationLogger;
import com.teamc.isow.backend.admin.AdminTargetType;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.dm.Conversation;
import com.teamc.isow.backend.dm.ConversationRepository;
import com.teamc.isow.backend.notification.NotificationEvents;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.report.AdminReportService;
import com.teamc.isow.backend.report.ReportCase;
import com.teamc.isow.backend.report.ReportCaseRepository;
import com.teamc.isow.backend.report.ReportReason;
import com.teamc.isow.backend.report.ReportTargetType;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理者の措置（docs/admin.md「6. 措置」）。管理者だけが使える（AdminAccess で確かめる）。
 *
 * <ul>
 *   <li>投稿は非公開にするだけ（消さない）。投稿者本人には見え、ほかの人からは存在しない投稿と同じになる（VisibilityConditions）</li>
 *   <li>利用停止にすると、すべての API が使えなくなり（SuspendedUserFilter）、ほかの人からは存在しないユーザーと同じになる。
 *       申請中・進行中の相談は終了する。解除しても、終了した相談は戻さない（Conversation.endBySystem）</li>
 *   <li>期限は保存するだけで、期限を過ぎたときに動く処理（ジョブ）はない。期限を過ぎたかは、使うたびに今の時刻と比べる</li>
 *   <li>すべての措置は、同じトランザクションで措置の履歴（moderation_actions）と管理操作のログに残す（残せなければ措置も取り消す）。
 *       ログには理由と期間だけを書き、メモ・投稿の内容・ユーザー名は書かない（個人を特定する情報になりうるため）</li>
 *   <li>本人への通知は、投稿の非公開化と公開に戻すときだけ（「誰が」は空。運営からの通知）。利用停止は、次に使おうとしたときに画面で伝える</li>
 * </ul>
 */
@Service
public class AdminModerationService {

    /** 利用停止の期間として選べる日数（null は無期限） */
    static final Set<Integer> SUSPENSION_DAYS = Set.of(1, 3, 7, 30);

    private final AdminAccess adminAccess;
    private final AdminOperationLogger operationLogger;
    private final ModerationActionRepository actionRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ReportCaseRepository caseRepository;
    private final AdminReportService adminReportService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AdminModerationService(AdminAccess adminAccess, AdminOperationLogger operationLogger,
            ModerationActionRepository actionRepository, PostRepository postRepository, UserRepository userRepository,
            ConversationRepository conversationRepository, ReportCaseRepository caseRepository,
            AdminReportService adminReportService, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.adminAccess = adminAccess;
        this.operationLogger = operationLogger;
        this.actionRepository = actionRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.caseRepository = caseRepository;
        this.adminReportService = adminReportService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    // ---- 投稿 ----

    @Transactional(readOnly = true)
    public ModerationResponses.PostState post(String subject, Long postId) {
        adminAccess.requireAdmin(subject);
        return postState(findPost(postId));
    }

    @Transactional
    public ModerationResponses.PostState hide(String subject, Long postId, ModerationRequests.Hide request) {
        User admin = adminAccess.requireAdmin(subject);
        ReportReason reason = parseReason(request.reason());
        if (!reason.appliesTo(ReportTargetType.POST)) {
            throw InputValidationException.of("reason", "投稿には使えない理由です");
        }
        validateMemo(request.memo());
        Post post = findPost(postId);
        ReportCase reportCase = linkedCase(request.reportCaseId(),
                c -> c.getTargetType() == ReportTargetType.POST && c.getTargetId().equals(postId));
        if (post.isHidden()) {
            throw new ModerationConflictException("この投稿はすでに非公開です。");
        }
        LocalDateTime now = now();
        post.hide(now, reason.name());
        postRepository.flush();
        record(ModerationActionType.HIDE_POST, post.getId(), post.getAuthor().getId(), reason.name(), null, reportCase,
                admin, request.memo(), now);
        operationLogger.recordByAdmin(admin, AdminAction.POST_HIDDEN, AdminTargetType.POST, post.getId(),
                "理由：" + reason.getLabel());
        resolveIfRequested(admin, reportCase, request.resolveReport());
        eventPublisher.publishEvent(new NotificationEvents.PostHidden(post.getId()));
        return postState(post);
    }

    @Transactional
    public ModerationResponses.PostState unhide(String subject, Long postId, ModerationRequests.Undo request) {
        User admin = adminAccess.requireAdmin(subject);
        validateMemo(request.memo());
        Post post = findPost(postId);
        if (!post.isHidden()) {
            throw new ModerationConflictException("この投稿は非公開ではありません。");
        }
        LocalDateTime now = now();
        post.unhide();
        postRepository.flush();
        record(ModerationActionType.UNHIDE_POST, post.getId(), post.getAuthor().getId(), null, null, null, admin,
                request.memo(), now);
        operationLogger.recordByAdmin(admin, AdminAction.POST_UNHIDDEN, AdminTargetType.POST, post.getId(),
                "非公開を解除");
        eventPublisher.publishEvent(new NotificationEvents.PostUnhidden(post.getId()));
        return postState(post);
    }

    @Transactional(readOnly = true)
    public ModerationResponses.HiddenPostList hiddenPosts(String subject, int page, int size) {
        adminAccess.requireAdmin(subject);
        Pageable pageable = PostCardService.pageRequest(page, size);
        Slice<Post> posts = postRepository.findHidden(pageable);
        List<ModerationResponses.HiddenPostItem> items = posts.getContent().stream()
                .map(p -> new ModerationResponses.HiddenPostItem(p.getId(), p.getTitle(), p.getAuthor().getUsername(),
                        p.getHiddenAt(), p.getHiddenReason(), label(p.getHiddenReason())))
                .toList();
        return new ModerationResponses.HiddenPostList(items, pageable.getPageNumber(), pageable.getPageSize(),
                posts.hasNext());
    }

    // ---- ユーザー ----

    @Transactional(readOnly = true)
    public ModerationResponses.UserState user(String subject, Long userId) {
        adminAccess.requireAdmin(subject);
        return userState(findUser(userId));
    }

    @Transactional(readOnly = true)
    public ModerationResponses.UserState userByUsername(String subject, String username) {
        adminAccess.requireAdmin(subject);
        String trimmed = username == null ? "" : username.strip();
        if (trimmed.startsWith("@")) {
            trimmed = trimmed.substring(1);
        }
        return userState(userRepository.findByUsername(trimmed)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @Transactional
    public ModerationResponses.UserState suspend(String subject, Long userId, ModerationRequests.Suspend request) {
        User admin = adminAccess.requireAdmin(subject);
        ReportReason reason = parseReason(request.reason());
        if (request.days() != null && !SUSPENSION_DAYS.contains(request.days())) {
            throw InputValidationException.of("days", "期間は1日・3日・7日・30日・無期限から選んでください");
        }
        validateMemo(request.memo());
        // 相談の承認など、ユーザーの行をロックする処理と同時に起きても、1つずつ処理する
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        ReportCase reportCase = linkedCase(request.reportCaseId(), c -> c.getTargetUserId().equals(userId));
        if (user.isAdmin()) {
            throw new ModerationConflictException("管理者は利用停止にできません。");
        }
        LocalDateTime now = now();
        if (user.isSuspendedAt(now)) {
            throw new ModerationConflictException("このユーザーはすでに利用停止中です。");
        }
        LocalDateTime until = request.days() == null ? null : now.plusDays(request.days());
        user.suspend(now, until, reason.name());
        userRepository.flush();
        // 申請中・進行中の相談は終了する（行をロックしてから。承認・終了と同時に起きても1つずつ処理する）
        for (Conversation found : conversationRepository.findOngoingOf(userId)) {
            conversationRepository.findByIdForUpdate(found.getId())
                    .filter(conversation -> conversation.getStatus().isOngoing())
                    .ifPresent(Conversation::endBySystem);
        }
        record(ModerationActionType.SUSPEND_USER, user.getId(), user.getId(), reason.name(), until, reportCase, admin,
                request.memo(), now);
        operationLogger.recordByAdmin(admin, AdminAction.USER_SUSPENDED, AdminTargetType.USER, user.getId(),
                "理由：" + reason.getLabel() + "、期間：" + (request.days() == null ? "無期限" : request.days() + "日"));
        resolveIfRequested(admin, reportCase, request.resolveReport());
        return userState(user);
    }

    @Transactional
    public ModerationResponses.UserState unsuspend(String subject, Long userId, ModerationRequests.Undo request) {
        User admin = adminAccess.requireAdmin(subject);
        validateMemo(request.memo());
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        LocalDateTime now = now();
        if (!user.isSuspendedAt(now)) {
            throw new ModerationConflictException("このユーザーは利用停止中ではありません。");
        }
        user.unsuspend();
        userRepository.flush();
        record(ModerationActionType.UNSUSPEND_USER, user.getId(), user.getId(), null, null, null, admin,
                request.memo(), now);
        operationLogger.recordByAdmin(admin, AdminAction.USER_UNSUSPENDED, AdminTargetType.USER, user.getId(),
                "利用停止を解除");
        return userState(user);
    }

    @Transactional(readOnly = true)
    public ModerationResponses.SuspendedUserList suspendedUsers(String subject, int page, int size) {
        adminAccess.requireAdmin(subject);
        Pageable pageable = PostCardService.pageRequest(page, size);
        Slice<User> users = userRepository.findSuspended(now(), pageable);
        List<ModerationResponses.SuspendedUserItem> items = users.getContent().stream()
                .map(u -> new ModerationResponses.SuspendedUserItem(u.getId(), u.getUsername(), u.getSuspendedAt(),
                        u.getSuspendedUntil(), u.getSuspensionReason(), label(u.getSuspensionReason())))
                .toList();
        return new ModerationResponses.SuspendedUserList(items, pageable.getPageNumber(), pageable.getPageSize(),
                users.hasNext());
    }

    // ---- 部品 ----

    private Post findPost(Long postId) {
        return postRepository.findDetailById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    /**
     * 結びつける通報。null なら結びつけない。存在しない通報・別の対象の通報は 400（措置もしない）。
     * 対応済みにする場合に備え、行をロックして読む
     */
    private ReportCase linkedCase(Long reportCaseId, Predicate<ReportCase> matchesTarget) {
        if (reportCaseId == null) {
            return null;
        }
        return caseRepository.findByIdForUpdate(reportCaseId)
                .filter(matchesTarget)
                .orElseThrow(() -> InputValidationException.of("reportCaseId", "この対象の通報ではありません"));
    }

    private void resolveIfRequested(User admin, ReportCase reportCase, Boolean resolveReport) {
        if (reportCase != null && Boolean.TRUE.equals(resolveReport)) {
            adminReportService.resolveWithModeration(admin, reportCase);
        }
    }

    private void record(ModerationActionType type, Long targetId, Long targetUserId, String reason,
            LocalDateTime suspendedUntil, ReportCase reportCase, User admin, String memo, LocalDateTime now) {
        actionRepository.save(new ModerationAction(type, targetId, targetUserId, reason, suspendedUntil,
                reportCase == null ? null : reportCase.getId(), admin, blankToNull(memo), now));
    }

    private ModerationResponses.PostState postState(Post post) {
        return new ModerationResponses.PostState(post.getId(), post.getTitle(), post.getAuthor().getId(),
                post.getAuthor().getUsername(), post.getCreatedAt(), post.isHidden(), post.getHiddenAt(),
                post.getHiddenReason(), label(post.getHiddenReason()), history("POST", post.getId()));
    }

    private ModerationResponses.UserState userState(User user) {
        boolean suspended = user.isSuspendedAt(now());
        return new ModerationResponses.UserState(user.getId(), user.getUsername(), user.isAdmin(), suspended,
                suspended ? user.getSuspendedAt() : null, suspended ? user.getSuspendedUntil() : null,
                suspended ? user.getSuspensionReason() : null, suspended ? label(user.getSuspensionReason()) : null,
                history("USER", user.getId()));
    }

    private List<ModerationResponses.HistoryItem> history(String targetType, Long targetId) {
        return actionRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(targetType, targetId).stream()
                .map(a -> new ModerationResponses.HistoryItem(a.getId(), a.getAction(), a.getReason(),
                        label(a.getReason()), a.getSuspendedUntil(), a.getReportCaseId(), a.getAdminUsername(),
                        a.getMemo(), a.getCreatedAt()))
                .toList();
    }

    private static ReportReason parseReason(String value) {
        try {
            return ReportReason.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw InputValidationException.of("reason", "理由を選んでください");
        }
    }

    private static void validateMemo(String memo) {
        if (memo != null && memo.length() > ModerationAction.MAX_MEMO_LENGTH) {
            throw InputValidationException.of("memo", "メモは" + ModerationAction.MAX_MEMO_LENGTH + "文字以内で入力してください");
        }
    }

    private static String label(String reason) {
        if (reason == null) {
            return null;
        }
        try {
            return ReportReason.valueOf(reason).getLabel();
        } catch (IllegalArgumentException e) {
            return reason;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
