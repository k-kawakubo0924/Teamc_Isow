package com.teamc.isow.backend.admin;

import com.teamc.isow.backend.user.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理操作のログを残す窓口（docs/admin.md「管理操作のログ」）。管理操作はすべてここから記録する。
 *
 * <p>すでに始まっている操作のトランザクションの中でしか呼べない（MANDATORY。外で呼ぶと IllegalTransactionStateException）。
 * ログの保存に失敗すると例外が操作の処理に伝わり、操作そのものも取り消される。
 * 通知の作成は「失敗しても本体の処理を続ける」方針で、それとは逆だが、通知は利便性のため、ログは責任の記録のためなので、意図的に逆にしている。
 *
 * <p>detail に個人を特定する情報（メールアドレスなど）を書かないこと（docs/admin.md）。
 */
@Component
public class AdminOperationLogger {

    private final AdminOperationLogRepository logRepository;

    public AdminOperationLogger(AdminOperationLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    /** 管理者の操作を記録する */
    @Transactional(propagation = Propagation.MANDATORY)
    public AdminOperationLog recordByAdmin(User admin, AdminAction action, AdminTargetType targetType, Long targetId,
            String detail) {
        return logRepository.save(AdminOperationLog.byAdmin(admin, action, targetType, targetId, detail));
    }

    /** システムの操作（起動時の昇格など、人が操作していないもの）を記録する */
    @Transactional(propagation = Propagation.MANDATORY)
    public AdminOperationLog recordBySystem(AdminAction action, AdminTargetType targetType, Long targetId, String detail) {
        return logRepository.save(AdminOperationLog.bySystem(action, targetType, targetId, detail));
    }
}
