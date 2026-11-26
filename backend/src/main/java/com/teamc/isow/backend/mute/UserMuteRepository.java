package com.teamc.isow.backend.mute;

import com.teamc.isow.backend.block.VisibilityConditions;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserMuteRepository extends JpaRepository<UserMute, Long> {

    /** muterId が mutedId をミュートしているか（片方向） */
    boolean existsByMuterIdAndMutedId(Long muterId, Long mutedId);

    /** ミュートを解除する（削除した件数を返す。ミュートしていなければ 0） */
    @Modifying
    @Query("DELETE FROM UserMute m WHERE m.muter.id = :muterId AND m.muted.id = :mutedId")
    int deleteByPair(@Param("muterId") Long muterId, @Param("mutedId") Long mutedId);

    /**
     * ミュートしている人の一覧：ミュートした新しい順（同じなら ID の大きい順）。相手のユーザーも一緒に読み込む。
     * ブロックの間柄になった相手・利用停止中の相手は出さない（存在しないユーザーと同じ扱い。行は残すため、解けると戻る）
     */
    @Query("""
            SELECT m FROM UserMute m JOIN FETCH m.muted u
            WHERE m.muter.id = :viewerId AND""" + VisibilityConditions.USER_VISIBLE + """
            ORDER BY m.mutedAt DESC, m.id DESC
            """)
    Slice<UserMute> findPageByMuterId(@Param("viewerId") Long viewerId, @Param("now") LocalDateTime now,
            Pageable pageable);
}
