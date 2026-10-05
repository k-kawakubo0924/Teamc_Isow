package com.teamc.isow.backend.dm;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 会話（conversations）。2人の組み合わせは user1Id < user2Id の順で渡すこと（Conversation のコメントを参照）。
 * 状態は定数名の文字列で持つため、引数には ConversationStatus.name() を渡す。
 */
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /**
     * 行をロックして読み込む（SELECT ... FOR UPDATE）。承認・拒否・終了が同時に届いても、1つずつ順に処理するために使う
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findByIdForUpdate(@Param("id") Long id);

    /** 2人の間の「申請中」または「進行中」の会話（一意制約により多くても1件） */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.user1.id = :user1Id AND c.user2.id = :user2Id AND c.ongoing = TRUE
            """)
    Optional<Conversation> findOngoing(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);

    /** 2人の間で、requesterId の申請が since より後に拒否された日時のうち、最も新しいもの。なければ null */
    @Query("""
            SELECT MAX(c.respondedAt) FROM Conversation c
            WHERE c.user1.id = :user1Id AND c.user2.id = :user2Id
              AND c.requestedBy.id = :requesterId AND c.status = :status AND c.respondedAt > :since
            """)
    LocalDateTime findLatestRespondedAt(
            @Param("user1Id") Long user1Id,
            @Param("user2Id") Long user2Id,
            @Param("requesterId") Long requesterId,
            @Param("status") String status,
            @Param("since") LocalDateTime since);

    /**
     * DM一覧。userId が参加している会話のうち、指定した状態のものを、最終メッセージの新しい順（メッセージがなければ申込日時）に読む。
     * 相手を表示するため、2人のユーザーも一緒に読み込む（会話ごとに SQL を発行しないため）。
     *
     * @param direction どちらが申し込んだ会話を含めるか（ConversationFilter.Direction.code。0 はどちらでも、1 は自分、2 は相手）
     * @param pattern 相手のユーザー名・表示名の絞り込み（小文字にした LIKE のパターン。SearchPatterns.contains で作る）
     */
    @Query("""
            SELECT c FROM Conversation c JOIN FETCH c.user1 u1 JOIN FETCH c.user2 u2
            WHERE c.status IN :statuses
              AND ((u1.id = :userId
                    AND (LOWER(u2.username) LIKE :pattern ESCAPE '\\' OR LOWER(u2.displayName) LIKE :pattern ESCAPE '\\'))
                OR (u2.id = :userId
                    AND (LOWER(u1.username) LIKE :pattern ESCAPE '\\' OR LOWER(u1.displayName) LIKE :pattern ESCAPE '\\')))
              AND (:direction = 0
                OR (:direction = 1 AND c.requestedBy.id = :userId)
                OR (:direction = 2 AND c.requestedBy.id <> :userId))
            ORDER BY COALESCE(c.lastMessageAt, c.requestedAt) DESC, c.id DESC
            """)
    Slice<Conversation> findForList(
            @Param("userId") Long userId,
            @Param("statuses") Collection<String> statuses,
            @Param("direction") int direction,
            @Param("pattern") String pattern,
            Pageable pageable);

    /** 会話1件と、2人のユーザーを一緒に読み込む（チャット画面の上部に相手を表示するため） */
    @Query("SELECT c FROM Conversation c JOIN FETCH c.user1 JOIN FETCH c.user2 WHERE c.id = :id")
    Optional<Conversation> findWithUsersById(@Param("id") Long id);

    /** userId が参加している会話のうち、どちらが申し込んだか（自分なら requestedByMe = true）と状態で数える */
    @Query("""
            SELECT COUNT(c) FROM Conversation c
            WHERE (c.user1.id = :userId OR c.user2.id = :userId) AND c.status = :status
              AND ((:requestedByMe = TRUE AND c.requestedBy.id = :userId)
                OR (:requestedByMe = FALSE AND c.requestedBy.id <> :userId))
            """)
    long countByDirection(
            @Param("userId") Long userId, @Param("status") String status, @Param("requestedByMe") boolean requestedByMe);

    /** userId が受けている（相手から申し込まれた）会話のうち、指定した状態のものの件数 */
    @Query("""
            SELECT COUNT(c) FROM Conversation c
            WHERE (c.user1.id = :userId OR c.user2.id = :userId)
              AND c.requestedBy.id <> :userId AND c.status = :status
            """)
    long countReceived(@Param("userId") Long userId, @Param("status") String status);
}
