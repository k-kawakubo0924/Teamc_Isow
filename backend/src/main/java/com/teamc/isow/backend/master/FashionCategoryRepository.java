package com.teamc.isow.backend.master;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FashionCategoryRepository extends JpaRepository<FashionCategory, Long> {

    boolean existsByName(String name);

    /** 選択肢として出すもの（有効なもの）を、並び順・ID順で返す */
    List<FashionCategory> findByActiveTrueOrderByDisplayOrderAscIdAsc();
}
