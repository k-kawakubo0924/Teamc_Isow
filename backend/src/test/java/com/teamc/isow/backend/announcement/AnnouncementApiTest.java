package com.teamc.isow.backend.announcement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.notification.Notification;
import com.teamc.isow.backend.notification.NotificationRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 利用者側のお知らせの API（GET /api/announcements ほか。docs/notification.md「お知らせ」）と、
 * 通知の未読件数のお知らせの分（GET /api/notifications/summary の unreadAnnouncementCount）の確認
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnnouncementApiTest {

    private static final String[] EMAILS = {"ann-api-me@example.com", "ann-api-other@example.com"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User other;
    /** me の登録日時（DB の値） */
    private LocalDateTime registeredAt;

    @BeforeEach
    void setUp() {
        // 件数を確かめるため、ほかで発行したお知らせを残さない（既読の記録は ON DELETE CASCADE で一緒に消える）
        jdbcTemplate.update("DELETE FROM announcements");
        me = userRepository.save(new User(EMAILS[0], "09000000160", "hash", "ann_api_me"));
        other = userRepository.save(new User(EMAILS[1], "09000000161", "hash", "ann_api_other"));
        registeredAt = jdbcTemplate.queryForObject(
                "SELECT created_at FROM users WHERE id = ?", Timestamp.class, me.getId()).toLocalDateTime();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM announcements");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", (Object[]) EMAILS);
    }

    // ---- 一覧 ----

    @Test
    void 一覧は新しい順に_題名_本文_発行日時_既読を返し_ページで区切る() throws Exception {
        Long first = insert("サービス開始", "<b>ISHO</b> を始めました", registeredAt.plusMinutes(1));
        Long second = insert("メンテナンスのお知らせ", "10月10日 2:00〜4:00\nご不便をおかけします。", registeredAt.plusMinutes(2));
        Long third = insert("新機能", "検索ができるようになりました。", registeredAt.plusMinutes(3));

        getWithToken("/api/announcements?size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.announcements[*].id", contains(third.intValue(), second.intValue())))
                .andExpect(jsonPath("$.announcements[1].title").value("メンテナンスのお知らせ"))
                // 本文は文字のまま返す（改行は \n。HTML として扱わない）
                .andExpect(jsonPath("$.announcements[1].body").value("10月10日 2:00〜4:00\nご不便をおかけします。"))
                .andExpect(jsonPath("$.announcements[1].publishedAt").isNotEmpty())
                .andExpect(jsonPath("$.announcements[1].read").value(false))
                // 管理者側の情報（発行者）は返さない
                .andExpect(jsonPath("$.announcements[1].publisherUsername").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
        getWithToken("/api/announcements?page=1&size=2")
                .andExpect(jsonPath("$.announcements[*].id", contains(first.intValue())))
                .andExpect(jsonPath("$.announcements[0].body").value("<b>ISHO</b> を始めました"))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ---- 既読 ----

    @Test
    void 開くと既読になり_2回目は最初の日時のまま_未読件数が減る() throws Exception {
        Long id = insert("お知らせ", "本文", registeredAt.plusMinutes(1));
        insert("別のお知らせ", "本文", registeredAt.plusMinutes(2));
        getWithToken("/api/notifications/summary").andExpect(jsonPath("$.unreadAnnouncementCount").value(2));

        postWithToken("/api/announcements/" + id + "/read").andExpect(status().isNoContent());
        LocalDateTime firstReadAt = readAt(me, id);
        assertThat(firstReadAt).isNotNull();
        postWithToken("/api/announcements/" + id + "/read").andExpect(status().isNoContent());

        assertThat(readAt(me, id)).isEqualTo(firstReadAt);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM announcement_reads WHERE user_id = ?",
                Integer.class, me.getId())).isEqualTo(1);
        getWithToken("/api/announcements")
                .andExpect(jsonPath("$.announcements[1].id").value(id))
                .andExpect(jsonPath("$.announcements[1].read").value(true))
                .andExpect(jsonPath("$.announcements[0].read").value(false));
        getWithToken("/api/notifications/summary").andExpect(jsonPath("$.unreadAnnouncementCount").value(1));
    }

    @Test
    void 既読は人ごとに持ち_他人が読んでも自分は未読のまま() throws Exception {
        Long id = insert("お知らせ", "本文", registeredAt.plusMinutes(1));

        mockMvc.perform(post("/api/announcements/" + id + "/read").header("Authorization", bearer(other)))
                .andExpect(status().isNoContent());

        getWithToken("/api/announcements").andExpect(jsonPath("$.announcements[0].read").value(false));
        getWithToken("/api/notifications/summary").andExpect(jsonPath("$.unreadAnnouncementCount").value(1));
    }

    @Test
    void 登録より前に発行されたお知らせは一覧に出すが_既読として扱い_未読件数に数えない() throws Exception {
        Long before = insert("登録前", "本文", registeredAt.minusDays(1));
        Long after = insert("登録後", "本文", registeredAt.plusMinutes(1));

        getWithToken("/api/announcements")
                .andExpect(jsonPath("$.announcements[*].id", contains(after.intValue(), before.intValue())))
                .andExpect(jsonPath("$.announcements[0].read").value(false))
                .andExpect(jsonPath("$.announcements[1].read").value(true));
        getWithToken("/api/notifications/summary").andExpect(jsonPath("$.unreadAnnouncementCount").value(1));
    }

    @Test
    void 存在しないお知らせの既読は404() throws Exception {
        postWithToken("/api/announcements/999999999/read")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("お知らせが見つかりません。"));
    }

    // ---- 通知の未読件数との関係 ----

    @Test
    void 通知の未読件数とお知らせの未読件数は別々に返し_すべて既読はお知らせに影響しない() throws Exception {
        notificationRepository.save(Notification.followed(me, other));
        insert("お知らせ", "本文", registeredAt.plusMinutes(1));

        getWithToken("/api/notifications/summary")
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.unreadAnnouncementCount").value(1));

        postWithToken("/api/notifications/read-all").andExpect(status().isNoContent());

        getWithToken("/api/notifications/summary")
                .andExpect(jsonPath("$.unreadCount").value(0))
                .andExpect(jsonPath("$.unreadAnnouncementCount").value(1));
    }

    // ---- 認証 ----

    @Test
    void 未ログインは401() throws Exception {
        Long id = insert("お知らせ", "本文", registeredAt.plusMinutes(1));

        mockMvc.perform(get("/api/announcements")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/announcements/" + id + "/read")).andExpect(status().isUnauthorized());
    }

    // ---- 部品 ----

    /** お知らせを発行日時を指定して作る（発行者は API の確認に関係しないため、me にしておく） */
    private Long insert(String title, String body, LocalDateTime publishedAt) {
        jdbcTemplate.update("""
                INSERT INTO announcements (title, body, published_at, publisher_id, publisher_username)
                VALUES (?, ?, ?, ?, 'ann_api_me')
                """, title, body, Timestamp.valueOf(publishedAt), me.getId());
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM announcements", Long.class);
    }

    private LocalDateTime readAt(User user, Long announcementId) {
        Timestamp value = jdbcTemplate.queryForObject(
                "SELECT read_at FROM announcement_reads WHERE user_id = ? AND announcement_id = ?",
                Timestamp.class, user.getId(), announcementId);
        return value.toLocalDateTime();
    }

    private ResultActions getWithToken(String path) throws Exception {
        return perform(get(path));
    }

    private ResultActions postWithToken(String path) throws Exception {
        return perform(post(path));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(me)));
    }

    private String bearer(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
