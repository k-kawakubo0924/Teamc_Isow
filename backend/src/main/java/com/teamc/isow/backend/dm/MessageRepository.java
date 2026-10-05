package com.teamc.isow.backend.dm;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** メッセージ（messages）。並び順には ID を使う（送信した順に増えるため。Message のインデックスのコメントを参照） */
public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * 会話のメッセージを新しい順に読む。beforeId を指定すると、それより古いものだけ（チャット画面で上にスクロールしたとき）。
     * 送信者は読み込まない（送信者の ID だけを使うため）
     */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.id = :conversationId AND (:beforeId IS NULL OR m.id < :beforeId)
            ORDER BY m.id DESC
            """)
    List<Message> findLatest(
            @Param("conversationId") Long conversationId, @Param("beforeId") Long beforeId, Pageable pageable);

    /** 会話のメッセージのうち、afterId より新しいものを古い順に読む（チャット画面を開いている間の取り直し） */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.id = :conversationId AND m.id > :afterId
            ORDER BY m.id ASC
            """)
    List<Message> findAfter(
            @Param("conversationId") Long conversationId, @Param("afterId") Long afterId, Pageable pageable);

    /** 会話で、userId 以外（相手）から届いた未読メッセージをすべて既読にする。更新した件数を返す */
    @Modifying
    @Query("""
            UPDATE Message m SET m.readAt = :now
            WHERE m.conversation.id = :conversationId AND m.sender.id <> :userId AND m.readAt IS NULL
            """)
    int markAsRead(
            @Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 各会話の最新メッセージ（DM一覧で、会話ごとに SQL を発行しないよう IN でまとめて読む） */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.id IN :conversationIds
              AND m.id = (SELECT MAX(m2.id) FROM Message m2 WHERE m2.conversation.id = m.conversation.id)
            """)
    List<Message> findLatestByConversationIds(@Param("conversationIds") Collection<Long> conversationIds);

    /** 各会話で、userId 以外（相手）から届いた未読メッセージのうち最新のもの（ホームの新着メッセージ）。未読がない会話は含まれない */
    @Query("""
            SELECT m FROM Message m
            WHERE m.conversation.id IN :conversationIds
              AND m.id = (SELECT MAX(m2.id) FROM Message m2
                          WHERE m2.conversation.id = m.conversation.id
                            AND m2.sender.id <> :userId AND m2.readAt IS NULL)
            """)
    List<Message> findLatestUnreadByConversationIds(
            @Param("conversationIds") Collection<Long> conversationIds, @Param("userId") Long userId);

    /** 各会話で、userId 以外（相手）から届いた未読メッセージの件数。未読がない会話は含まれない */
    @Query("""
            SELECT m.conversation.id AS conversationId, COUNT(m) AS unreadCount FROM Message m
            WHERE m.conversation.id IN :conversationIds AND m.sender.id <> :userId AND m.readAt IS NULL
            GROUP BY m.conversation.id
            """)
    List<UnreadCount> countUnread(
            @Param("conversationIds") Collection<Long> conversationIds, @Param("userId") Long userId);

    /** userId が参加している、指定した状態の会話で、相手から届いた未読メッセージの合計（下部ナビの DM のバッジに使う） */
    @Query("""
            SELECT COUNT(m) FROM Message m JOIN m.conversation c
            WHERE (c.user1.id = :userId OR c.user2.id = :userId) AND c.status IN :statuses
              AND m.sender.id <> :userId AND m.readAt IS NULL
            """)
    long countUnreadTotal(@Param("userId") Long userId, @Param("statuses") Collection<String> statuses);

    /** countUnread の結果の1行 */
    interface UnreadCount {

        Long getConversationId();

        long getUnreadCount();
    }
}
