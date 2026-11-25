package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.post.PostCardService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理操作のログの一覧（docs/admin.md「管理操作のログ」）。管理者だけが見られる */
@Service
public class AdminOperationLogQueryService {

    private final AdminAccess adminAccess;
    private final AdminOperationLogRepository logRepository;

    public AdminOperationLogQueryService(AdminAccess adminAccess, AdminOperationLogRepository logRepository) {
        this.adminAccess = adminAccess;
        this.logRepository = logRepository;
    }

    /**
     * 新しい順に返す。page は 0 から。範囲外の page・size はエラーにせず、
     * 0 以上・1〜50 に丸める（ホームの一覧・通知一覧と同じ PostCardService.pageRequest）。
     * 操作した人のユーザー名はログの列に持つため、ユーザーの表とは結合しない
     */
    @Transactional(readOnly = true)
    public AdminOperationLogListResponse list(String subject, int page, int size) {
        adminAccess.requireAdmin(subject);
        Pageable pageable = PostCardService.pageRequest(page, size);
        Slice<AdminOperationLog> logs = logRepository.findAllByOrderByOperatedAtDescIdDesc(pageable);
        return new AdminOperationLogListResponse(
                logs.getContent().stream().map(AdminOperationLogListResponse.Item::from).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), logs.hasNext());
    }
}
