package com.teamc.isow.backend.dm;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
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

    /** userId が受けている（相手から申し込まれた）会話のうち、指定した状態のものの件数 */
    @Query("""
            SELECT COUNT(c) FROM Conversation c
            WHERE (c.user1.id = :userId OR c.user2.id = :userId)
              AND c.requestedBy.id <> :userId AND c.status = :status
            """)
    long countReceived(@Param("userId") Long userId, @Param("status") String status);
}
