package com.teamc.isow.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.dm.Conversation;
import com.teamc.isow.backend.dm.ConversationRepository;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.post.Post;
import com.teamc.isow.backend.post.PostRepository;
import com.teamc.isow.backend.tag.TagRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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

/** 通知の API（GET /api/notifications ほか。docs/notification.md）の確認 */
@SpringBootTest
@AutoConfigureMockMvc
class NotificationApiTest {

    private static final String[] EMAILS = {"notif-api-me@example.com", "notif-api-b@example.com",
        "notif-api-c@example.com"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User me;
    private User b;
    private User c;

    @BeforeEach
    void setUp() {
        me = userRepository.save(new User(EMAILS[0], "09000000090", "hash", "notif_api_me"));
        b = userRepository.save(new User(EMAILS[1], "09000000091", "hash", "notif_api_b"));
        c = userRepository.save(new User(EMAILS[2], "09000000092", "hash", "notif_api_c"));
    }

    @AfterEach
    void tearDown() {
        // 通知はユーザー・会話・投稿を参照しているため先に消す
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?, ?)", (Object[]) EMAILS);
    }

    // ---- 一覧 ----

    @Test
    void 自分の通知だけを通知日時の新しい順に返し_文面は返さず種類と相手を返す() throws Exception {
        jdbcTemplate.update("UPDATE users SET profile_image_url = 'http://localhost/uploads/b.jpg' WHERE id = ?",
                b.getId());
        Post post = savePost(me, "http://localhost/uploads/first.jpg", "http://localhost/uploads/second.jpg");
        Conversation conversation = conversationRepository.save(new Conversation(b, me));
        Notification liked = notificationRepository.save(Notification.liked(post, b));
        Notification followed = notificationRepository.save(Notification.followed(me, c));
        Notification requested = notificationRepository.save(Notification.consultationRequested(conversation));
        // 他人宛ての通知は返さない
        notificationRepository.save(Notification.followed(b, me));
        setNotifiedAt(liked, 2);
        setNotifiedAt(followed, 3);
        setNotifiedAt(requested, 1);

        getWithToken("/api/notifications")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[*].id", contains(requested.getId().intValue(),
                        liked.getId().intValue(), followed.getId().intValue())))
                .andExpect(jsonPath("$.notifications[0].type").value("CONSULTATION_REQUESTED"))
                .andExpect(jsonPath("$.notifications[0].conversationId").value(conversation.getId()))
                .andExpect(jsonPath("$.notifications[0].post").value(nullValue()))
                .andExpect(jsonPath("$.notifications[1].type").value("LIKED"))
                .andExpect(jsonPath("$.notifications[1].actor.id").value(b.getId()))
                .andExpect(jsonPath("$.notifications[1].actor.username").value("notif_api_b"))
                .andExpect(jsonPath("$.notifications[1].actor.profileImageUrl").value("http://localhost/uploads/b.jpg"))
                .andExpect(jsonPath("$.notifications[1].post.id").value(post.getId()))
                .andExpect(jsonPath("$.notifications[1].post.thumbnailUrl").value("http://localhost/uploads/first.jpg"))
                .andExpect(jsonPath("$.notifications[1].conversationId").value(nullValue()))
                .andExpect(jsonPath("$.notifications[1].notifiedAt").isNotEmpty())
                .andExpect(jsonPath("$.notifications[1].read").value(false))
                .andExpect(jsonPath("$.notifications[2].type").value("FOLLOWED"))
                .andExpect(jsonPath("$.notifications[2].actor.profileImageUrl").value(nullValue()))
                // 文面・個人情報は返さない
                .andExpect(jsonPath("$.notifications[0].message").doesNotExist())
                .andExpect(jsonPath("$.notifications[0].actor.email").doesNotExist());
    }

    @Test
    void ページで区切る() throws Exception {
        for (int i = 0; i < 3; i++) {
            notificationRepository.save(Notification.followed(me, b));
        }

        getWithToken("/api/notifications?page=0&size=2")
                .andExpect(jsonPath("$.notifications.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.hasNext").value(true));
        getWithToken("/api/notifications?page=1&size=2")
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void 通知がなければ空() throws Exception {
        getWithToken("/api/notifications")
                .andExpect(jsonPath("$.notifications", empty()))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    // ---- 未読件数 ----

    @Test
    void 未読件数は自分の未読だけを数え_メッセージの通知は含めない() throws Exception {
        Conversation conversation = conversationRepository.save(new Conversation(b, me));
        notificationRepository.save(Notification.followed(me, b));
        notificationRepository.save(Notification.liked(savePost(me), c));
        notificationRepository.save(Notification.messageReceived(conversation, b));
        Notification read = notificationRepository.save(Notification.followed(me, c));
        jdbcTemplate.update("UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE id = ?", read.getId());
        notificationRepository.save(Notification.followed(b, me));

        getWithToken("/api/notifications/summary")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    // ---- 既読・未読・削除 ----

    @Test
    void 既読にして_未読に戻せる_既読の日時は最初のまま() throws Exception {
        Notification notification = notificationRepository.save(Notification.followed(me, b));

        postWithToken("/api/notifications/" + notification.getId() + "/read").andExpect(status().isNoContent());
        Timestamp first = readAt(notification);
        assertThat(first).isNotNull();
        postWithToken("/api/notifications/" + notification.getId() + "/read").andExpect(status().isNoContent());
        assertThat(readAt(notification)).isEqualTo(first);
        getWithToken("/api/notifications").andExpect(jsonPath("$.notifications[0].read").value(true));

        postWithToken("/api/notifications/" + notification.getId() + "/unread").andExpect(status().isNoContent());
        assertThat(readAt(notification)).isNull();
    }

    @Test
    void 削除できる() throws Exception {
        Notification notification = notificationRepository.save(Notification.followed(me, b));

        mockMvc.perform(withToken(delete("/api/notifications/" + notification.getId())))
                .andExpect(status().isNoContent());

        assertThat(notificationRepository.existsById(notification.getId())).isFalse();
    }

    @Test
    void 他人の通知と存在しない通知は_同じ404で何も変えない() throws Exception {
        Notification others = notificationRepository.save(Notification.followed(b, c));
        jdbcTemplate.update("UPDATE notifications SET read_at = CURRENT_TIMESTAMP WHERE id = ?", others.getId());
        Timestamp readAt = readAt(others);

        for (long id : List.of(others.getId(), 999_999L)) {
            postWithToken("/api/notifications/" + id + "/read")
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("通知が見つかりません。"));
            postWithToken("/api/notifications/" + id + "/unread").andExpect(status().isNotFound());
            mockMvc.perform(withToken(delete("/api/notifications/" + id))).andExpect(status().isNotFound());
        }

        assertThat(notificationRepository.existsById(others.getId())).isTrue();
        assertThat(readAt(others)).isEqualTo(readAt);
    }

    @Test
    void すべて既読にすると_自分の通知はメッセージも含めて既読になり_他人の通知は変わらない() throws Exception {
        Conversation conversation = conversationRepository.save(new Conversation(b, me));
        Notification followed = notificationRepository.save(Notification.followed(me, b));
        Notification message = notificationRepository.save(Notification.messageReceived(conversation, b));
        Notification others = notificationRepository.save(Notification.followed(b, me));

        postWithToken("/api/notifications/read-all").andExpect(status().isNoContent());

        assertThat(readAt(followed)).isNotNull();
        assertThat(readAt(message)).isNotNull();
        assertThat(readAt(others)).isNull();
        getWithToken("/api/notifications/summary").andExpect(jsonPath("$.unreadCount").value(0));
    }

    // ---- 認証 ----

    @Test
    void 未ログインはすべて401() throws Exception {
        Notification notification = notificationRepository.save(Notification.followed(me, b));
        String base = "/api/notifications/";

        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(base + "summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(base + notification.getId() + "/read")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(base + notification.getId() + "/unread")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(base + notification.getId())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(base + "read-all")).andExpect(status().isUnauthorized());

        assertThat(readAt(notification)).isNull();
        assertThat(notificationRepository.existsById(notification.getId())).isTrue();
    }

    // ---- 部品 ----

    private ResultActions getWithToken(String url) throws Exception {
        return mockMvc.perform(withToken(get(url)));
    }

    private ResultActions postWithToken(String url) throws Exception {
        return mockMvc.perform(withToken(post(url)));
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request) {
        return request.header("Authorization", "Bearer " + token(me.getId()));
    }

    private Post savePost(User author, String... imageUrls) {
        List<String> urls = imageUrls.length == 0 ? List.of("http://localhost/uploads/n.jpg") : List.of(imageUrls);
        return postRepository.save(new Post(author, "題名",
                fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0), null, "説明", null,
                urls, List.of(tagRepository.findByOfficialTrueAndActiveTrueOrderByDisplayOrderAscIdAsc().get(0))));
    }

    /** 通知日時を hoursAgo 時間前にする */
    private void setNotifiedAt(Notification notification, int hoursAgo) {
        jdbcTemplate.update("UPDATE notifications SET notified_at = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now().minusHours(hoursAgo)), notification.getId());
    }

    private Timestamp readAt(Notification notification) {
        return jdbcTemplate.queryForObject(
                "SELECT read_at FROM notifications WHERE id = ?", Timestamp.class, notification.getId());
    }

    private String token(long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
