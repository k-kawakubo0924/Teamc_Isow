package com.teamc.isow.backend.dm;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.common.SearchPatterns;
import com.teamc.isow.backend.user.User;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DM一覧・会話1件・DM の件数（docs/dm.md「DM一覧」）。
 *
 * <p>会話ごとに SQL を発行しないよう、件数に関係なく次の4本で読む（ページが空なら 2本）。
 * <ol>
 *   <li>ログイン中のユーザー</li>
 *   <li>会話と、2人のユーザー（JOIN FETCH）</li>
 *   <li>各会話の最新メッセージ（IN でまとめて）</li>
 *   <li>各会話の未読件数（GROUP BY でまとめて）</li>
 * </ol>
 */
@Service
public class ConversationListService {

    /** 1ページあたりの件数の上限（他の一覧と同じ） */
    static final int MAX_PAGE_SIZE = 50;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final AuthService authService;

    public ConversationListService(
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            AuthService authService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.authService = authService;
    }

    /**
     * page は 0 から。範囲外の page・size はエラーにせず、0 以上・1〜MAX_PAGE_SIZE に丸める（他の一覧と同じ）。
     *
     * @param query 相手のユーザー名・表示名の一部（大文字小文字を区別しない）。null・空なら絞り込まない
     */
    @Transactional(readOnly = true)
    public ConversationListResponse list(String subject, ConversationFilter filter, String query, int page, int size) {
        User me = authService.requireCurrentUser(subject);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        Slice<Conversation> conversations = conversationRepository.findForList(
                me.getId(), filter.statusNames(), filter.direction().code, SearchPatterns.contains(query), pageable);

        List<Long> ids = conversations.getContent().stream().map(Conversation::getId).toList();
        Map<Long, Message> latestMessages = ids.isEmpty()
                ? Map.of()
                : messageRepository.findLatestByConversationIds(ids).stream()
                        .collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity()));
        Map<Long, Long> unreadCounts = ids.isEmpty()
                ? Map.of()
                : messageRepository.countUnread(ids, me.getId()).stream()
                        .collect(Collectors.toMap(
                                MessageRepository.UnreadCount::getConversationId,
                                MessageRepository.UnreadCount::getUnreadCount));

        List<ConversationListResponse.Item> items = conversations.getContent().stream()
                .map(c -> toItem(c, me, latestMessages.get(c.getId()), unreadCounts.getOrDefault(c.getId(), 0L)))
                .toList();
        return new ConversationListResponse(items, pageable.getPageNumber(), pageable.getPageSize(), conversations.hasNext());
    }

    /**
     * 会話1件（チャット画面の上部）。当事者でなければ、存在しない場合と同じ ConversationNotFoundException。
     * SQL はログイン中のユーザーと、会話・2人のユーザー（JOIN FETCH）の2本
     */
    @Transactional(readOnly = true)
    public ConversationDetailResponse get(String subject, Long conversationId) {
        User me = authService.requireCurrentUser(subject);
        Conversation conversation = ConversationChecks.requireParticipant(
                conversationRepository.findWithUsersById(conversationId), conversationId, me.getId());
        return new ConversationDetailResponse(
                conversation.getId(),
                conversation.getStatus(),
                conversation.getRequestedBy().getId().equals(me.getId()),
                partner(conversation, me),
                conversation.getRequestedAt(),
                conversation.getRespondedAt(),
                conversation.getEndedAt());
    }

    /**
     * DM の件数（下部ナビのバッジと、DM一覧の上下の件数）。SQL はログイン中のユーザーと、件数ごとに1本ずつの計4本。
     * 未読メッセージは、やり取り中の会話（進行中・終了）だけを数える（申請中の一言は、受け取った申請の件数として数える）
     */
    @Transactional(readOnly = true)
    public ConversationSummaryResponse summary(String subject) {
        Long userId = authService.requireCurrentUser(subject).getId();
        String requested = ConversationStatus.REQUESTED.name();
        return new ConversationSummaryResponse(
                messageRepository.countUnreadTotal(userId, ConversationFilter.CHATS.statusNames()),
                conversationRepository.countByDirection(userId, requested, false),
                conversationRepository.countByDirection(userId, requested, true));
    }

    /** latest はメッセージがなければ null */
    private static ConversationListResponse.Item toItem(Conversation conversation, User me, Message latest, long unread) {
        return new ConversationListResponse.Item(
                conversation.getId(),
                conversation.getStatus(),
                conversation.getRequestedBy().getId().equals(me.getId()),
                partner(conversation, me),
                latest == null ? null : latest.getBody(),
                latest != null && latest.getImageUrl() != null,
                latest == null ? null : latest.getSentAt(),
                unread,
                conversation.getRequestedAt());
    }

    private static ConversationListResponse.Partner partner(Conversation conversation, User me) {
        User partner = conversation.otherParticipant(me);
        return new ConversationListResponse.Partner(
                partner.getId(), partner.getUsername(), partner.getDisplayName(), partner.getProfileImageUrl());
    }
}
