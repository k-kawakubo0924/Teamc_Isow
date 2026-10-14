package com.teamc.isow.backend.announcement;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.user.User;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 利用者側のお知らせ（docs/notification.md「お知らせ」）。一覧・既読にする・未読件数。
 *
 * <ul>
 *   <li>存在しないお知らせを既読にしようとしたら 404</li>
 *   <li>登録より前に発行されたお知らせは一覧に出すが、既読として扱い、未読件数に数えない
 *       （登録した直後にバッジが溜まった状態にならないように）</li>
 *   <li>一覧・未読件数とも、件数に関係なく決まった本数の SQL で取得する</li>
 * </ul>
 */
@Service
public class AnnouncementQueryService {

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReadRepository announcementReadRepository;
    private final AuthService authService;
    private final TransactionTemplate transactionTemplate;

    public AnnouncementQueryService(AnnouncementRepository announcementRepository,
            AnnouncementReadRepository announcementReadRepository, AuthService authService,
            TransactionTemplate transactionTemplate) {
        this.announcementRepository = announcementRepository;
        this.announcementReadRepository = announcementReadRepository;
        this.authService = authService;
        this.transactionTemplate = transactionTemplate;
    }

    /** 新しい順に返す。page は 0 から。範囲外の page・size はエラーにせず丸める（通知の一覧と同じ） */
    @Transactional(readOnly = true)
    public AnnouncementListResponse list(String subject, int page, int size) {
        User user = authService.requireCurrentUser(subject);
        Pageable pageable = PostCardService.pageRequest(page, size);
        Slice<AnnouncementRow> rows = announcementRepository.findPageForUser(user.getId(), pageable);
        List<AnnouncementListResponse.Item> items = rows.getContent().stream()
                .map(row -> new AnnouncementListResponse.Item(row.id(), row.title(), row.body(), row.publishedAt(),
                        row.readAt() != null || row.publishedAt().isBefore(user.getCreatedAt())))
                .toList();
        return new AnnouncementListResponse(items, pageable.getPageNumber(), pageable.getPageSize(), rows.hasNext());
    }

    /**
     * 既読にする。すでに既読なら最初に開いた日時のまま。
     * 連打で同じリクエストがほぼ同時に届くと、両方が「未読」と判断して片方が主キーの重複になる。
     * その場合は先に届いた方で既読になっているため、成功として扱う（いいねの登録と同じ）
     */
    public void markRead(String subject, Long announcementId) {
        User user = authService.requireCurrentUser(subject);
        AnnouncementReadId id = new AnnouncementReadId(user.getId(), announcementId);
        try {
            transactionTemplate.executeWithoutResult(status -> {
                if (!announcementRepository.existsById(announcementId)) {
                    throw new AnnouncementNotFoundException(announcementId);
                }
                if (!announcementReadRepository.existsById(id)) {
                    announcementReadRepository.saveAndFlush(new AnnouncementRead(
                            user, announcementRepository.getReferenceById(announcementId), LocalDateTime.now()));
                }
            });
        } catch (DataIntegrityViolationException e) {
            if (!announcementReadRepository.existsById(id)) {
                throw e;
            }
        }
    }

    /** 未読のお知らせの件数（ベルのバッジ・「お知らせ」タブのバッジ用） */
    @Transactional(readOnly = true)
    public long countUnread(User user) {
        return announcementRepository.countUnread(user.getId(), user.getCreatedAt());
    }
}
