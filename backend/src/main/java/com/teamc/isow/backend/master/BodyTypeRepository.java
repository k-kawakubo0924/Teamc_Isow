package com.teamc.isow.backend.master;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BodyTypeRepository extends JpaRepository<BodyType, Long> {

    boolean existsByName(String name);
}
