package com.teamc.isow.backend.master;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FashionCategoryRepository extends JpaRepository<FashionCategory, Long> {

    boolean existsByName(String name);
}
