package com.teamc.isow.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 管理操作のログ（docs/admin.md「管理操作のログ」）の記録・変更できないこと・外部キーを張らないことの確認 */
@SpringBootTest
class AdminOperationLogTest {

    private static final String ADMIN_EMAIL = "oplog-admin@example.com";

    @Autowired
    private AdminOperationLogger logger;

    @Autowired
    private AdminOperationLogRepository logRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private User admin;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        admin = userRepository.save(new User(ADMIN_EMAIL, "09000000130", "hash", "oplog_admin"));
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM admin_operation_logs");
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", ADMIN_EMAIL);
    }

    @Test
    void 管理者の操作として記録すると_操作した人のIDと_その時点のユーザー名が入る() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        tx.executeWithoutResult(status -> logger.recordByAdmin(
                admin, AdminAction.MASTER_CREATED, AdminTargetType.FASHION_CATEGORY, 5L, "名前：フェミニン"));

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(((Number) row.get("operator_id")).longValue()).isEqualTo(admin.getId());
        assertThat(row.get("operator_username")).isEqualTo("oplog_admin");
        assertThat(row.get("action")).isEqualTo("MASTER_CREATED");
        assertThat(row.get("target_type")).isEqualTo("FASHION_CATEGORY");
        assertThat(((Number) row.get("target_id")).longValue()).isEqualTo(5L);
        assertThat(row.get("detail")).isEqualTo("名前：フェミニン");
        assertThat(((java.sql.Timestamp) row.get("operated_at")).toLocalDateTime()).isAfter(before);
        // 接続元の IP アドレスの列は作らない
        assertThat(row.keySet()).noneMatch(column -> column.toLowerCase().contains("ip"));
    }

    @Test
    void システムの操作として記録すると_操作した人のIDは空で_ユーザー名にシステムと入る() {
        tx.executeWithoutResult(status -> logger.recordBySystem(
                AdminAction.USER_PROMOTED_TO_ADMIN, AdminTargetType.USER, admin.getId(), "起動時の昇格"));

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT * FROM admin_operation_logs");
        assertThat(row.get("operator_id")).isNull();
        assertThat(row.get("operator_username")).isEqualTo(AdminOperationLog.SYSTEM_OPERATOR_NAME);
        assertThat(AdminOperationLog.SYSTEM_OPERATOR_NAME).isEqualTo("システム");
    }

    @Test
    void あとでユーザー名が変わっても_過去のログのユーザー名は変わらない() {
        tx.executeWithoutResult(status -> logger.recordByAdmin(
                admin, AdminAction.MASTER_CREATED, AdminTargetType.TAG, 1L, null));

        jdbcTemplate.update("UPDATE users SET username = 'renamed_admin' WHERE id = ?", admin.getId());

        assertThat(jdbcTemplate.queryForObject("SELECT operator_username FROM admin_operation_logs", String.class))
                .isEqualTo("oplog_admin");
    }

    @Test
    void 操作した人に外部キーを張らないため_ユーザーを消してもログは残る() {
        tx.executeWithoutResult(status -> logger.recordByAdmin(
                admin, AdminAction.MASTER_CREATED, AdminTargetType.TAG, 1L, null));

        // 将来ユーザーを物理削除する機能を作っても、削除が失敗したりログが消えたりしない
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", admin.getId());

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_operation_logs", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT operator_username FROM admin_operation_logs", String.class))
                .isEqualTo("oplog_admin");
    }

    @Test
    void トランザクションの外で記録しようとするとエラーになる() {
        // 操作と同じトランザクションで記録するための決まり（操作の外で単独で記録させない）
        assertThatThrownBy(() -> logger.recordBySystem(
                AdminAction.USER_PROMOTED_TO_ADMIN, AdminTargetType.USER, 1L, null))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM admin_operation_logs", Integer.class)).isZero();
    }

    @Test
    void 保存済みのログの値を書き換えても_DBには反映されない() {
        Long id = tx.execute(status -> logger.recordByAdmin(
                admin, AdminAction.MASTER_CREATED, AdminTargetType.TAG, 1L, "元の内容").getId());

        tx.executeWithoutResult(status -> {
            AdminOperationLog log = logRepository.findById(id).orElseThrow();
            setField(log, "detail", "書き換えた内容");
            setField(log, "action", "MASTER_DEACTIVATED");
            entityManager.flush();
        });

        Map<String, Object> row = jdbcTemplate.queryForMap("SELECT detail, action FROM admin_operation_logs WHERE id = ?", id);
        assertThat(row.get("detail")).isEqualTo("元の内容");
        assertThat(row.get("action")).isEqualTo("MASTER_CREATED");
    }

    @Test
    void ログのリポジトリには削除や更新のメソッドがない() {
        Method[] methods = AdminOperationLogRepository.class.getMethods();

        assertThat(Arrays.stream(methods).map(Method::getName))
                .noneMatch(name -> name.startsWith("delete") || name.startsWith("update") || name.startsWith("remove"));
    }

    /** 値を変えるメソッドがないため、リフレクションで書き換える（変更が DB に反映されないことを確かめるため） */
    private static void setField(AdminOperationLog target, String name, Object value) {
        try {
            Field field = AdminOperationLog.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
