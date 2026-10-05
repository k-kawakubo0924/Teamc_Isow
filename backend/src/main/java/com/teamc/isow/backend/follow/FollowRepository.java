package com.teamc.isow.backend.follow;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, Long> {

    /** 有効なフォローがあるか */
    @Query("""
            SELECT COUNT(f) > 0 FROM Follow f
            WHERE f.follower.id = :followerId AND f.followee.id = :followeeId AND f.active = true
            """)
    boolean existsActive(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    /** 指定した日時より後に解除したフォローのうち、最後に解除したもの（解除から5分以内の再フォローの判定に使う） */
    Optional<Follow> findFirstByFollowerIdAndFolloweeIdAndUnfollowedAtAfterOrderByUnfollowedAtDesc(
            Long followerId, Long followeeId, LocalDateTime since);

    /** フォローを解除する。有効なフォローがなければ何もしない（更新した件数を返す） */
    @Modifying
    @Query("""
            UPDATE Follow f SET f.unfollowedAt = :now, f.active = null
            WHERE f.follower.id = :followerId AND f.followee.id = :followeeId AND f.active = true
            """)
    int unfollow(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId,
            @Param("now") LocalDateTime now);

    /** 解除したフォローを有効に戻す。すでに有効なら何もしない（更新した件数を返す） */
    @Modifying
    @Query("""
            UPDATE Follow f SET f.unfollowedAt = null, f.active = true, f.restoredAt = :now
            WHERE f.id = :id AND f.active IS NULL
            """)
    int restore(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** フォロー数（有効なフォローのみ） */
    long countByFollowerIdAndActiveTrue(Long followerId);

    /** フォロワー数（有効なフォローのみ） */
    long countByFolloweeIdAndActiveTrue(Long followeeId);

    /** フォロー中一覧（有効なフォローのみ）。並び順は pageable で指定する */
    @Query(value = """
            SELECT f FROM Follow f JOIN FETCH f.followee
            WHERE f.follower.id = :followerId AND f.active = true
            """)
    Slice<Follow> findActiveFollowings(@Param("followerId") Long followerId, Pageable pageable);

    /** フォロー中一覧（有効なフォローと、since より後に解除したもの）。本人が自分の一覧を見る場合に使う */
    @Query(value = """
            SELECT f FROM Follow f JOIN FETCH f.followee
            WHERE f.follower.id = :followerId AND (f.active = true OR f.unfollowedAt > :since)
            """)
    Slice<Follow> findFollowingsIncludingUnfollowedSince(
            @Param("followerId") Long followerId, @Param("since") LocalDateTime since, Pageable pageable);

    /** フォロワー一覧（有効なフォローのみ）。並び順は pageable で指定する */
    @Query(value = """
            SELECT f FROM Follow f JOIN FETCH f.follower
            WHERE f.followee.id = :followeeId AND f.active = true
            """)
    Slice<Follow> findActiveFollowers(@Param("followeeId") Long followeeId, Pageable pageable);

    /** 指定したユーザーのうち、followerId のユーザーが有効にフォローしているユーザーの ID（1回の SQL で調べる） */
    @Query("""
            SELECT f.followee.id FROM Follow f
            WHERE f.follower.id = :followerId AND f.active = true AND f.followee.id IN :followeeIds
            """)
    List<Long> findFollowingIds(@Param("followerId") Long followerId,
            @Param("followeeIds") Collection<Long> followeeIds);
}
