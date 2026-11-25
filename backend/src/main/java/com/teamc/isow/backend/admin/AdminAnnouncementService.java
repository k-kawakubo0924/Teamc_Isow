package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.announcement.Announcement;
import com.teamc.isow.backend.announcement.AnnouncementRepository;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.user.User;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * お知らせの発行・取り消しと、発行済みの一覧（docs/admin.md「お知らせの発行」）。管理者だけが使える。
 * 発行・取り消しは、同じトランザクションで管理操作のログを残す（残せなければ操作も元に戻す）。訂正はできない
 */
@Service
public class AdminAnnouncementService {

    private final AdminAccess adminAccess;
    private final AdminOperationLogger operationLogger;
    private final AnnouncementRepository announcementRepository;

    public AdminAnnouncementService(AdminAccess adminAccess, AdminOperationLogger operationLogger,
            AnnouncementRepository announcementRepository) {
        this.adminAccess = adminAccess;
        this.operationLogger = operationLogger;
        this.announcementRepository = announcementRepository;
    }

    /** 発行する。題名・本文は AdminAnnouncementRequest で整え、確認済みであること */
    @Transactional
    public AdminAnnouncementResponse publish(String subject, AdminAnnouncementRequest request) {
        User admin = adminAccess.requireAdmin(subject);
        Announcement announcement = announcementRepository.save(new Announcement(admin, request.title(), request.body()));
        operationLogger.recordByAdmin(admin, AdminAction.ANNOUNCEMENT_PUBLISHED, AdminTargetType.ANNOUNCEMENT,
                announcement.getId(), "題名：" + announcement.getTitle());
        return AdminAnnouncementResponse.from(announcement);
    }

    /**
     * 取り消す。行は消さず、取り消した日時と管理者を残す（利用者側では一覧にも未読件数にも出さなくなる）。
     * 存在しなければ 404、すでに取り消していれば 409（ログも残さない）。
     * 同時に取り消しが届いても1回だけ取り消すよう、行をロックして読み込む
     */
    @Transactional
    public AdminAnnouncementResponse withdraw(String subject, Long id) {
        User admin = adminAccess.requireAdmin(subject);
        Announcement announcement = announcementRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (announcement.isWithdrawn()) {
            throw new AnnouncementAlreadyWithdrawnException(id);
        }
        announcement.withdraw(admin, LocalDateTime.now());
        operationLogger.recordByAdmin(admin, AdminAction.ANNOUNCEMENT_WITHDRAWN, AdminTargetType.ANNOUNCEMENT,
                id, "題名：" + announcement.getTitle());
        return AdminAnnouncementResponse.from(announcement);
    }

    /**
     * 新しい順に返す（取り消したものも含む）。page は 0 から。範囲外の page・size はエラーにせず、
     * 0 以上・1〜50 に丸める（管理操作のログの一覧と同じ PostCardService.pageRequest）
     */
    @Transactional(readOnly = true)
    public AdminAnnouncementResponse.ListResponse list(String subject, int page, int size) {
        adminAccess.requireAdmin(subject);
        Pageable pageable = PostCardService.pageRequest(page, size);
        Slice<Announcement> announcements = announcementRepository.findAllByOrderByPublishedAtDescIdDesc(pageable);
        return new AdminAnnouncementResponse.ListResponse(
                announcements.getContent().stream().map(AdminAnnouncementResponse::from).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), announcements.hasNext());
    }
}
