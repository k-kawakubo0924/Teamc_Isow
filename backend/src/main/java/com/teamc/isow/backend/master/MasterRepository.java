package com.teamc.isow.backend.master;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * ファッションの種類・骨格タイプ・パーソナルカラーのリポジトリに共通の操作。
 * 3つは同じ作り（MasterEntity）のため、管理画面のマスタ管理（AdminMasterService）は種類を指定してこの共通の操作を使う
 */
@NoRepositoryBean
public interface MasterRepository<T extends MasterEntity> extends JpaRepository<T, Long> {

    boolean existsByName(String name);

    /** 選択肢として出すもの（有効なもの）を、並び順・ID順で返す */
    List<T> findByActiveTrueOrderByDisplayOrderAscIdAsc();

    /** 無効なものも含めてすべてを、並び順・ID順で返す（管理画面の一覧用） */
    List<T> findAllByOrderByDisplayOrderAscIdAsc();
}
