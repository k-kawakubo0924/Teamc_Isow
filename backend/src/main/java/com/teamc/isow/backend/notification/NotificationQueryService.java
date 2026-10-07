package com.teamc.isow.backend.notification;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.post.PostCardService;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.post.PostThumbnail;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通知一覧と既読・未読・削除（docs/notification.md）。自分の通知だけを扱う。
 *
 * <p>他人の通知と存在しない通知は、どちらも NotificationNotFoundException（404）にする（他人の通知の有無が分からないようにするため）。
 * 一覧は件数に関係なく決まった本数の SQL で取得する（通知と関連するユーザーを1本、投稿のサムネイルをまとめて1本）。
 */
@Service
public class NotificationQueryService {

    private final NotificationRepository notificationRepository;
    private final PostRepository postRepository;
    private final AuthService authService;

    public NotificationQueryService(
            NotificationRepository notificationRepository, PostRepository postRepository, AuthService authService) {
        this.notificationRepository = notificationRepository;
        this.postRepository = postRepository;
        this.authService = authService;
    }

    /** 自分の通知を通知日時の新しい順に返す。page は 0 から。範囲外の page・size はエラーにせず丸める（ホームの一覧と同じ） */
    @Transactional(readOnly = true)
    public NotificationListResponse list(String subject, int page, int size) {
        Long userId = authService.requireCurrentUser(subject).getId();
        Pageable pageable = PostCardService.pageRequest(page, size);
        Slice<Notification> notifications = notificationRepository.findPageByRecipientId(userId, pageable);

        // 関連する投稿は ID だけを使う（投稿の行は読まない）。サムネイルはページ分をまとめて読む
        List<Long> postIds = notifications.getContent().stream()
                .map(Notification::getPost)
                .filter(Objects::nonNull)
                .map(post -> post.getId())
                .distinct()
                .toList();
        Map<Long, String> thumbnails = postIds.isEmpty()
                ? Map.of()
                : postRepository.findThumbnails(postIds).stream()
                        .collect(Collectors.toMap(PostThumbnail::postId, PostThumbnail::imageUrl));

        List<NotificationListResponse.Item> items = notifications.getContent().stream()
                .map(notification -> toItem(notification, thumbnails))
                .toList();
        return new NotificationListResponse(
                items, pageable.getPageNumber(), pageable.getPageSize(), notifications.hasNext());
    }

    /** 未読件数（ベルのバッジ用）。メッセージの通知は DM のバッジで別に数えるため含めない */
    @Transactional(readOnly = true)
    public NotificationSummaryResponse summary(String subject) {
        Long userId = authService.requireCurrentUser(subject).getId();
        return new NotificationSummaryResponse(notificationRepository.countUnreadExcludingType(
                userId, NotificationType.MESSAGE_RECEIVED.name()));
    }

    /** 既読にする。すでに既読なら最初に既読にした日時のまま */
    @Transactional
    public void markRead(String subject, Long notificationId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        requireFound(notificationRepository.markRead(notificationId, userId, LocalDateTime.now()), notificationId);
    }

    /** 未読に戻す（通知一覧の「未読に戻す」） */
    @Transactional
    public void markUnread(String subject, Long notificationId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        requireFound(notificationRepository.markUnread(notificationId, userId), notificationId);
    }

    /** 削除する（通知一覧の「通知の削除」） */
    @Transactional
    public void delete(String subject, Long notificationId) {
        Long userId = authService.requireCurrentUser(subject).getId();
        requireFound(notificationRepository.deleteOwn(notificationId, userId), notificationId);
    }

    /** 自分の未読の通知をすべて既読にする（メッセージの通知も含む） */
    @Transactional
    public void markAllRead(String subject) {
        Long userId = authService.requireCurrentUser(subject).getId();
        notificationRepository.markAllRead(userId, LocalDateTime.now());
    }

    private static void requireFound(int updated, Long notificationId) {
        if (updated == 0) {
            throw new NotificationNotFoundException(notificationId);
        }
    }

    private static NotificationListResponse.Item toItem(Notification notification, Map<Long, String> thumbnails) {
        Long postId = notification.getPost() == null ? null : notification.getPost().getId();
        Long conversationId = notification.getConversation() == null ? null : notification.getConversation().getId();
        return new NotificationListResponse.Item(
                notification.getId(),
                notification.getType(),
                new NotificationListResponse.Actor(
                        notification.getActor().getId(),
                        notification.getActor().getUsername(),
                        notification.getActor().getProfileImageUrl()),
                postId == null ? null : new NotificationListResponse.PostSummary(postId, thumbnails.get(postId)),
                conversationId,
                notification.getNotifiedAt(),
                notification.isRead());
    }
}
