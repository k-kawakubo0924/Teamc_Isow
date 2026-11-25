package com.teamc.isow.backend.user;

import com.teamc.isow.backend.block.VisibilityConditions;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** メールアドレスは正規化（小文字化）済みの値で渡すこと */
    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /** 管理画面の措置で、ユーザー名から探す（docs/admin.md「6. 措置」） */
    Optional<User> findByUsername(String username);

    /** 管理画面の措置：いま利用停止中のユーザー（期限を過ぎたものは含めない）。停止した日時の新しい順 */
    @Query("""
            SELECT u FROM User u
            WHERE u.suspendedAt IS NOT NULL AND (u.suspendedUntil IS NULL OR u.suspendedUntil > :now)
            ORDER BY u.suspendedAt DESC, u.id DESC
            """)
    Slice<User> findSuspended(@Param("now") LocalDateTime now, Pageable pageable);

    /** メールアドレスは正規化（小文字化）済みの値で渡すこと */
    Optional<User> findByEmail(String email);

    /** プロフィールの表示用に、骨格タイプ・パーソナルカラーも一緒に読み込む（別々に SQL を発行しないため） */
    @Query("""
            SELECT u FROM User u LEFT JOIN FETCH u.bodyType LEFT JOIN FETCH u.personalColor
            WHERE u.id = :id
            """)
    Optional<User> findWithProfileById(@Param("id") Long id);

    /**
     * 行をロックして読み込む（SELECT ... FOR UPDATE。トランザクションの終わりまで、他のロックを待たせる）。
     * ユーザーごとの件数の上限を確認してから追加する処理（相談の承認など）を、同時に実行させないために使う
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * ユーザーの検索（docs/search.md。SearchService）：ユーザー名・表示名の部分一致。viewerId のユーザー（自分）と、
     * viewerId とブロックの間柄にあるユーザー・利用停止中のユーザーは含めない（VisibilityConditions。件数も同じ）。
     * ユーザー名か表示名の完全一致 → 前方一致 → 部分一致 → ユーザー名の順に並べる。
     * 値は SearchPatterns で作る（pattern は contains（null なら絞り込まない）、exact は exact、prefix は startsWith）
     */
    @Query(value = """
            SELECT u FROM User u
            WHERE u.id <> :viewerId
              AND""" + VisibilityConditions.USER_VISIBLE + """
              AND (:pattern IS NULL
                   OR LOWER(u.username) LIKE :pattern ESCAPE '\\'
                   OR LOWER(u.displayName) LIKE :pattern ESCAPE '\\')
            ORDER BY
              CASE WHEN LOWER(u.username) = :exact OR LOWER(u.displayName) = :exact THEN 0
                   WHEN LOWER(u.username) LIKE :prefix ESCAPE '\\' OR LOWER(u.displayName) LIKE :prefix ESCAPE '\\' THEN 1
                   ELSE 2 END,
              u.username
            """,
            countQuery = """
            SELECT COUNT(u) FROM User u
            WHERE u.id <> :viewerId
              AND""" + VisibilityConditions.USER_VISIBLE + """
              AND (:pattern IS NULL
                   OR LOWER(u.username) LIKE :pattern ESCAPE '\\'
                   OR LOWER(u.displayName) LIKE :pattern ESCAPE '\\')
            """)
    Page<User> search(@Param("viewerId") Long viewerId, @Param("now") LocalDateTime now, @Param("pattern") String pattern,
            @Param("exact") String exact, @Param("prefix") String prefix, Pageable pageable);

    /** userIds のうち、now の時点で利用停止中のユーザーの ID（User.isSuspendedAt と同じ判定） */
    @Query("""
            SELECT u.id FROM User u
            WHERE u.id IN :userIds AND u.suspendedAt IS NOT NULL
              AND (u.suspendedUntil IS NULL OR u.suspendedUntil > :now)
            """)
    List<Long> findSuspendedIdsAmong(@Param("userIds") Collection<Long> userIds, @Param("now") LocalDateTime now);
}
