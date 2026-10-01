package com.teamc.isow.backend.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** メールアドレスは正規化（小文字化）済みの値で渡すこと */
    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    /** メールアドレスは正規化（小文字化）済みの値で渡すこと */
    Optional<User> findByEmail(String email);
}
