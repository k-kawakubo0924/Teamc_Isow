package com.teamc.isow.backend.admin;

import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.Repository;

/**
 * 管理操作のログの保存と読み込み。追記だけにするため、JpaRepository（削除・一括更新のメソッドを持つ）は使わず、
 * 保存と読み込みのメソッドだけを持たせる（コードから削除・更新を呼ぶ手段をなくす）
 */
public interface AdminOperationLogRepository extends Repository<AdminOperationLog, Long> {

    /** 保存する。AdminOperationLogger から、操作と同じトランザクションで呼ぶこと */
    AdminOperationLog save(AdminOperationLog log);

    Optional<AdminOperationLog> findById(Long id);

    /** 新しい順（同じ日時なら ID の大きい順）。操作した人のユーザー名は列に持つため、結合しない */
    Slice<AdminOperationLog> findAllByOrderByOperatedAtDescIdDesc(Pageable pageable);
}
