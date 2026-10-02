package com.teamc.isow.backend.tag;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

    /** TagNameNormalizer.key() で正規化済みの値で渡すこと */
    boolean existsByNormalizedName(String normalizedName);
}
