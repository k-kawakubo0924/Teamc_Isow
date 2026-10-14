package com.teamc.isow.backend.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

/**
 * お知らせの一覧と未読件数で、1件ごとに SQL が発行されていないこと（N+1 問題）の確認。
 * 5件と20件で SQL の本数が同じであることを確かめる。既読・未読・登録前のお知らせを混ぜる
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class AnnouncementQueryCountTest {

    private static final String ME_EMAIL = "ann-count-me@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private LocalDateTime registeredAt;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM announcements");
        me = userRepository.save(new User(ME_EMAIL, "09000000170", "hash", "ann_count_me"));
        registeredAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM users WHERE id = ?", Timestamp.class, me.getId()).toLocalDateTime();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM announcements");
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", ME_EMAIL);
    }

    @Test
    void 一覧と未読件数は件数に関係なくSQLの本数が一定() throws Exception {
        createAnnouncements(0, 5);
        long list5 = countSql("/api/announcements?size=20", 5);
        long summary5 = countSql("/api/notifications/summary", -1);

        createAnnouncements(5, 20);
        long list20 = countSql("/api/announcements?size=20", 20);
        long summary20 = countSql("/api/notifications/summary", -1);

        System.out.printf("### SQL の本数（5件 → 20件）: お知らせの一覧 %d → %d、未読件数 %d → %d%n",
                list5, list20, summary5, summary20);
        assertThat(list20).isEqualTo(list5);
        assertThat(summary20).isEqualTo(summary5);
    }

    /** from 番目から to 番目の手前まで作る。3件に1件は登録前、3件に1件は既読にする */
    private void createAnnouncements(int from, int to) {
        for (int i = from; i < to; i++) {
            LocalDateTime publishedAt = i % 3 == 0 ? registeredAt.minusDays(1) : registeredAt.plusMinutes(i + 1);
            jdbcTemplate.update("""
                    INSERT INTO announcements (title, body, published_at, publisher_id, publisher_username)
                    VALUES (?, ?, ?, ?, 'ann_count_me')
                    """, "お知らせ" + i, "本文" + i, Timestamp.valueOf(publishedAt), me.getId());
            Long id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM announcements", Long.class);
            if (i % 3 == 1) {
                jdbcTemplate.update("INSERT INTO announcement_reads (user_id, announcement_id, read_at) VALUES (?, ?, ?)",
                        me.getId(), id, Timestamp.valueOf(LocalDateTime.now()));
            }
        }
    }

    /** API を1回呼び、発行された SQL の本数を返す。expectedItems が 0 以上なら、一覧の件数も確かめる */
    private long countSql(String path, int expectedItems) throws Exception {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var result = mockMvc.perform(get(path).header("Authorization", bearer())).andExpect(status().isOk());
        if (expectedItems >= 0) {
            result.andExpect(jsonPath("$.announcements.length()").value(expectedItems));
        }
        return statistics.getPrepareStatementCount();
    }

    private String bearer() {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(me.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
