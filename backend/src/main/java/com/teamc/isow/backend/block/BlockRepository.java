package com.teamc.isow.backend.block;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BlockRepository extends JpaRepository<Block, Long> {

    /** blockerId が blockedId をブロックしているか（片方向） */
    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /** 2人がブロックの間柄にあるか（どちらの向きでも true。効果は両方向のため） */
    @Query("""
            SELECT COUNT(bk) > 0 FROM Block bk
            WHERE (bk.blocker.id = :userId1 AND bk.blocked.id = :userId2)
               OR (bk.blocker.id = :userId2 AND bk.blocked.id = :userId1)
            """)
    boolean existsBetween(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    /**
     * userIds のうち、viewerId とブロックの間柄にある人の ID（どちらの向きでも。人数に関係なく SQL は1本）
     */
    @Query("""
            SELECT CASE WHEN bk.blocker.id = :viewerId THEN bk.blocked.id ELSE bk.blocker.id END FROM Block bk
            WHERE (bk.blocker.id = :viewerId AND bk.blocked.id IN :userIds)
               OR (bk.blocked.id = :viewerId AND bk.blocker.id IN :userIds)
            """)
    List<Long> findHiddenAmong(@Param("viewerId") Long viewerId, @Param("userIds") Collection<Long> userIds);

    /** ブロックを解除する（削除した件数を返す。ブロックしていなければ 0） */
    @Modifying
    @Query("DELETE FROM Block bk WHERE bk.blocker.id = :blockerId AND bk.blocked.id = :blockedId")
    int deleteByPair(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /** ブロック中の一覧：ブロックした新しい順（同じなら ID の大きい順）。相手のユーザーも一緒に読み込む */
    @Query("""
            SELECT bk FROM Block bk JOIN FETCH bk.blocked
            WHERE bk.blocker.id = :blockerId
            ORDER BY bk.blockedAt DESC, bk.id DESC
            """)
    Slice<Block> findPageByBlockerId(@Param("blockerId") Long blockerId, Pageable pageable);
}
