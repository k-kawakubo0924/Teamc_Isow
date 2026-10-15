package com.teamc.isow.backend.contact;

import com.teamc.isow.backend.admin.AdminAccess;
import com.teamc.isow.backend.admin.AdminAction;
import com.teamc.isow.backend.admin.AdminOperationLogger;
import com.teamc.isow.backend.admin.AdminTargetType;
import com.teamc.isow.backend.common.InputValidationException;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理者がお問い合わせを見る・状態を記録する（docs/admin.md「7. お問い合わせ」）。管理者だけが使える（AdminAccess で確かめる）。
 *
 * <ul>
 *   <li>一覧には内容・メールアドレスを出さない。詳細でだけ出す</li>
 *   <li><b>詳細を見たことはログに残さない</b>。DM は当事者同士のもので、管理者が読んだこと自体を記録する必要があるが、
 *       お問い合わせは本人が運営に宛てて送ったもので、読むこと自体が目的のため（docs/admin.md「7. お問い合わせ」）。
 *       「DM とそろっていない」と考えて記録を足さないこと</li>
 *   <li>状態の変更は、同じトランザクションで管理操作のログに残す（残せなければ変更も取り消す）。
 *       ログの内容は状態だけで、内容・メールアドレス・メモ・ユーザー名は書かない</li>
 * </ul>
 */
@Service
public class AdminContactService {

    private final AdminAccess adminAccess;
    private final AdminOperationLogger operationLogger;
    private final ContactInquiryRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    public AdminContactService(AdminAccess adminAccess, AdminOperationLogger operationLogger,
            ContactInquiryRepository repository, UserRepository userRepository, Clock clock) {
        this.adminAccess = adminAccess;
        this.operationLogger = operationLogger;
        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    /** 一覧（新しい順）。status・category・channel は定数名（null・空なら絞らない。status の既定は画面で OPEN） */
    @Transactional(readOnly = true)
    public AdminContactResponses.ListResponse list(String subject, String status, String category, String channel,
            int page, int size) {
        adminAccess.requireAdmin(subject);
        String statusFilter = filter(ContactStatus.class, status, "status");
        String categoryFilter = filter(ContactCategory.class, category, "category");
        String channelFilter = filter(ContactChannel.class, channel, "channel");
        Pageable pageable = PostCardService.pageRequest(page, size);
        Slice<ContactInquiry> inquiries = repository.findPage(statusFilter, categoryFilter, channelFilter, pageable);
        // 送った人の今のユーザー名（ページ分をまとめて読む。退会していれば出さない）
        Map<Long, String> usernames = userRepository.findAllById(
                        inquiries.getContent().stream().map(ContactInquiry::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getUsername));
        List<AdminContactResponses.ListItem> items = inquiries.getContent().stream()
                .map(c -> new AdminContactResponses.ListItem(c.getId(), c.getChannel(), c.getCategory(), c.getStatus(),
                        c.getUserId(), usernames.get(c.getUserId()), c.getCreatedAt(), c.getClosedAt()))
                .toList();
        return new AdminContactResponses.ListResponse(items, pageable.getPageNumber(), pageable.getPageSize(),
                inquiries.hasNext());
    }

    /** 詳細。見たことはログに残さない（上記） */
    @Transactional(readOnly = true)
    public AdminContactResponses.Detail detail(String subject, Long id) {
        adminAccess.requireAdmin(subject);
        ContactInquiry c = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String username = userRepository.findById(c.getUserId()).map(User::getUsername).orElse(null);
        return new AdminContactResponses.Detail(c.getId(), c.getChannel(), c.getCategory(), c.getStatus(), c.getUserId(),
                username, c.getEmail(), c.getBody(), c.getCreatedAt(), c.getClosedAt(), c.getHandledByUsername(),
                c.getAdminMemo());
    }

    /** 状態を変える（対応済み・対応不要・未対応に戻す）。同じトランザクションでログに残す。すでにその状態なら 409 */
    @Transactional
    public AdminContactResponses.StatusResult changeStatus(String subject, Long id, AdminContactStatusRequest request) {
        User admin = adminAccess.requireAdmin(subject);
        ContactStatus newStatus = parse(ContactStatus.class, request.status(), "status");
        if (request.memo() != null && request.memo().length() > ContactInquiry.MAX_MEMO_LENGTH) {
            throw InputValidationException.of("memo", "メモは" + ContactInquiry.MAX_MEMO_LENGTH + "文字以内で入力してください");
        }
        ContactInquiry inquiry = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (inquiry.getStatus() == newStatus) {
            throw new AdminContactConflictException("すでに「" + newStatus.getLabel() + "」です。");
        }
        inquiry.changeStatus(newStatus, admin, request.memo(), LocalDateTime.now(clock));
        repository.flush();
        AdminAction action = switch (newStatus) {
            case RESOLVED -> AdminAction.CONTACT_RESOLVED;
            case DISMISSED -> AdminAction.CONTACT_DISMISSED;
            case OPEN -> AdminAction.CONTACT_REOPENED;
        };
        // ログの内容は状態だけ（内容・メールアドレス・メモ・ユーザー名は書かない）
        operationLogger.recordByAdmin(admin, action, AdminTargetType.CONTACT, id, "状態：" + newStatus.getLabel());
        return new AdminContactResponses.StatusResult(inquiry.getId(), inquiry.getStatus(), inquiry.getClosedAt(),
                inquiry.getHandledByUsername(), inquiry.getAdminMemo());
    }

    /** 絞り込みの値。空なら null、定数名でなければ 400 */
    private static <E extends Enum<E>> String filter(Class<E> type, String value, String field) {
        return value == null || value.isBlank() ? null : parse(type, value, field).name();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw InputValidationException.of(field, "値が正しくありません");
        }
    }
}
