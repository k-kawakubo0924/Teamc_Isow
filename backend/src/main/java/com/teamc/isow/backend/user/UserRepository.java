package com.teamc.isow.backend.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    /** メールアドレスは正規化（小文字化）済みの値で渡すこと */
    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /** メールアドレスは正規化（小文字化）済みの値で渡すこと */
    Optional<User> findByEmail(String email);

    /** プロフィールの表示用に、骨格タイプ・パーソナルカラーも一緒に読み込む（別々に SQL を発行しないため） */
    @Query("""
            SELECT u FROM User u LEFT JOIN FETCH u.bodyType LEFT JOIN FETCH u.personalColor
            WHERE u.id = :id
            """)
    Optional<User> findWithProfileById(@Param("id") Long id);
}
