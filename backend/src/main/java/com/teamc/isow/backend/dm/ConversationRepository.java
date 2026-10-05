package com.teamc.isow.backend.dm;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    /** userId と partnerIds の各相手との間の「申請中」または「進行中」の会話（一意制約により、相手ごとに多くても1件） */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.ongoing = TRUE
              AND ((c.user1.id = :userId AND c.user2.id IN :partnerIds)
                OR (c.user2.id = :userId AND c.user1.id IN :partnerIds))
            """)
    List<Conversation> findOngoingWith(@Param("userId") Long userId, @Param("partnerIds") Collection<Long> partnerIds);

    /** requesterId が partnerIds の各相手に送った申請のうち、指定した状態（拒否）で、since より後に応答されたもの */
    @Query("""
            SELECT c FROM Conversation c
            WHERE c.requestedBy.id = :requesterId AND c.status = :status AND c.respondedAt > :since
              AND ((c.user1.id = :requesterId AND c.user2.id IN :partnerIds)
                OR (c.user2.id = :requesterId AND c.user1.id IN :partnerIds))
            """)
    List<Conversation> findRejectedSince(
            @Param("requesterId") Long requesterId,
            @Param("partnerIds") Collection<Long> partnerIds,
            @Param("status") String status,
            @Param("since") LocalDateTime since);

    /**
     * userIds の各ユーザーが受けている（相手から申し込まれた）、指定した状態の会話の件数のうち、そのユーザーが user1 側のもの。
     * user2 側の分は countReceivedAsUser2 で数え、足し合わせる（1本の SQL で「どちらか側」をまとめると GROUP BY が複雑になるため）。
     * 件数が 0 のユーザーは含まれない
     */
    @Query("""
            SELECT c.user1.id AS userId, COUNT(c) AS count FROM Conversation c
            WHERE c.status = :status AND c.user1.id IN :userIds AND c.requestedBy.id <> c.user1.id
            GROUP BY c.user1.id
            """)
    List<UserCount> countReceivedAsUser1(@Param("userIds") Collection<Long> userIds, @Param("status") String status);

    /** countReceivedAsUser1 の、そのユーザーが user2 側のもの */
    @Query("""
            SELECT c.user2.id AS userId, COUNT(c) AS count FROM Conversation c
            WHERE c.status = :status AND c.user2.id IN :userIds AND c.requestedBy.id <> c.user2.id
            GROUP BY c.user2.id
            """)
    List<UserCount> countReceivedAsUser2(@Param("userIds") Collection<Long> userIds, @Param("status") String status);

    /** countReceivedAsUser1・countReceivedAsUser2 の結果の1行 */
    interface UserCount {

        Long getUserId();

        long getCount();
    }

    /**
     * DM一覧。userId が参加している会話のうち、指定した状態のものを、最終メッセージの新しい順（メッセージがなければ申込日時）に読む。
     * 相手を表示するため、2人のユーザーも一緒に読み込む（会話ごとに SQL を発行しないため）。
     *
     * @param direction どちらが申し込んだ会話を含めるか（ConversationFilter.Direction.code。0 はどちらでも、1 は自分、2 は相手）
     * @param pattern 相手のユーザー名・表示名の絞り込み（小文字にした LIKE のパターン。SearchPatterns.contains で作る）
     * @param unreadOnly true なら、相手から届いた未読メッセージがある会話だけ（ホームの新着メッセージ）
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
              AND (:unreadOnly = FALSE OR EXISTS (
                    SELECT m.id FROM Message m
                    WHERE m.conversation = c AND m.sender.id <> :userId AND m.readAt IS NULL))
            ORDER BY COALESCE(c.lastMessageAt, c.requestedAt) DESC, c.id DESC
            """)
    Slice<Conversation> findForList(
            @Param("userId") Long userId,
            @Param("statuses") Collection<String> statuses,
            @Param("direction") int direction,
            @Param("pattern") String pattern,
            @Param("unreadOnly") boolean unreadOnly,
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
