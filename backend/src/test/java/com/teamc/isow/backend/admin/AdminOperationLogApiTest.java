package com.teamc.isow.backend.admin;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 管理操作のログの一覧（GET /api/admin/operation-logs）の確認 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminOperationLogApiTest {

    private static final String ADMIN_EMAIL = "oplog-api-admin@example.com";
    private static final String USER_EMAIL = "oplog-api-user@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminOperationLogger logger;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private User admin;
    private User user;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(new User(ADMIN_EMAIL, "09000000140", "hash", "oplog_api_admin"));
        user = userRepository.save(new User(USER_EMAIL, "09000000141", "hash", "oplog_api_user"));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM admin_operation_logs");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", ADMIN_EMAIL, USER_EMAIL);
    }

    @Test
    void 新しい順に返し_システムの操作はシステムと分かる() throws Exception {
        Long older = record(false, "古い操作", 2);
        Long newer = record(true, "新しい操作", 1);

        mockMvc.perform(get("/api/admin/operation-logs").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs[*].id", contains(newer.intValue(), older.intValue())))
                .andExpect(jsonPath("$.logs[0].system").value(true))
                .andExpect(jsonPath("$.logs[0].operatorId").value(nullValue()))
                .andExpect(jsonPath("$.logs[0].operatorUsername").value("システム"))
                .andExpect(jsonPath("$.logs[0].action").value("USER_PROMOTED_TO_ADMIN"))
                .andExpect(jsonPath("$.logs[0].targetType").value("USER"))
                .andExpect(jsonPath("$.logs[0].detail").value("新しい操作"))
                .andExpect(jsonPath("$.logs[0].operatedAt").isNotEmpty())
                .andExpect(jsonPath("$.logs[1].system").value(false))
                .andExpect(jsonPath("$.logs[1].operatorId").value(admin.getId()))
                .andExpect(jsonPath("$.logs[1].operatorUsername").value("oplog_api_admin"))
                .andExpect(jsonPath("$.logs[1].action").value("MASTER_CREATED"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void sizeは1から50に丸め_負のpageは0として扱う() throws Exception {
        record(false, "1件目", 1);

        mockMvc.perform(get("/api/admin/operation-logs?size=1000").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(50));
        mockMvc.perform(get("/api/admin/operation-logs?size=0").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.size").value(1));
        mockMvc.perform(get("/api/admin/operation-logs?page=-3").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.logs.length()").value(1));
    }

    @Test
    void ページで区切る() throws Exception {
        for (int i = 0; i < 3; i++) {
            record(false, "操作" + i, 3 - i);
        }

        mockMvc.perform(get("/api/admin/operation-logs?page=0&size=2").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.logs.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
        mockMvc.perform(get("/api/admin/operation-logs?page=1&size=2").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.logs.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 一般の利用者は404_トークンなしは401() throws Exception {
        mockMvc.perform(get("/api/admin/operation-logs").header("Authorization", bearer(user)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/operation-logs")).andExpect(status().isUnauthorized());
    }

    /** ログを1件残し、操作した日時を hoursAgo 時間前にする */
    private Long record(boolean system, String detail, int hoursAgo) {
        Long id = new TransactionTemplate(transactionManager).execute(status -> (system
                ? logger.recordBySystem(AdminAction.USER_PROMOTED_TO_ADMIN, AdminTargetType.USER, user.getId(), detail)
                : logger.recordByAdmin(admin, AdminAction.MASTER_CREATED, AdminTargetType.TAG, 1L, detail)).getId());
        jdbcTemplate.update("UPDATE admin_operation_logs SET operated_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(hoursAgo)), id);
        return id;
    }

    private String bearer(User target) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(target.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
