package com.teamc.isow.backend.master;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalColorRepository extends JpaRepository<PersonalColor, Long> {

    boolean existsByName(String name);
}
