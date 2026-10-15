package com.teamc.isow.backend.moderation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    /** 対象の措置の履歴を、新しい順に返す */
    List<ModerationAction> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(String targetType, Long targetId);
}
