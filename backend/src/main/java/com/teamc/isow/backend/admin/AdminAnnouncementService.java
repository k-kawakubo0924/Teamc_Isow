package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.announcement.Announcement;
import com.teamc.isow.backend.announcement.AnnouncementRepository;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.user.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * お知らせの発行と、発行済みの一覧（docs/admin.md「お知らせの発行」）。管理者だけが使える。
 * 発行は、同じトランザクションで管理操作のログを残す（残せなければ発行も取り消す）
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
     * 新しい順に返す。page は 0 から。範囲外の page・size はエラーにせず、
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
