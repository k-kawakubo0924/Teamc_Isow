package com.teamc.isow.backend.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

/**
 * 通報のテストで使う登場人物と操作（docs/admin.md「通報」）。
 * 投稿・メッセージは画面と同じ API で作り、写真のファイルも実際に保存する（証拠のコピーを確かめるため）
 */
final class ReportFixture {

    static final String EMAIL_PATTERN = "rpt-%@example.com";

    final MockMvc mockMvc;
    private final JwtEncoder jwtEncoder;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;

    /** 通報する人 */
    User reporter;
    /** 通報される人 */
    User target;
    /** 2人目の通報する人 */
    User reporter2;
    /** 管理者 */
    User admin;

    ReportFixture(MockMvc mockMvc, JwtEncoder jwtEncoder, UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jwtEncoder = jwtEncoder;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    void createUsers() {
        reporter = userRepository.save(new User("rpt-reporter@example.com", "09000000200", "hash", "rpt_reporter"));
        target = userRepository.save(new User("rpt-target@example.com", "09000000201", "hash", "rpt_target"));
        reporter2 = userRepository.save(new User("rpt-reporter2@example.com", "09000000202", "hash", "rpt_reporter2"));
        admin = userRepository.save(new User("rpt-admin@example.com", "09000000203", "hash", "rpt_admin"));
        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.getId());
    }

    /** テストで作ったものを消す（参照している側から消す） */
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM admin_operation_logs");
        jdbcTemplate.update("DELETE FROM report_evidence_files");
        jdbcTemplate.update("DELETE FROM reports");
        jdbcTemplate.update("DELETE FROM report_cases");
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM likes");
        jdbcTemplate.update("DELETE FROM favorites");
        jdbcTemplate.update("DELETE FROM follows");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM tags WHERE is_official = false");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?", EMAIL_PATTERN);
    }

    // ---- 投稿・メッセージを作る（画面と同じ API） ----

    /** 写真 photos 枚の投稿を作り、その ID を返す */
    long createPost(User author, String title, int photos) throws Exception {
        var request = multipart("/api/posts");
        for (int i = 0; i < photos; i++) {
            request.file(new MockMultipartFile("images", i + ".png", "image/png", image(20 + i, 20, "png")));
        }
        request.param("title", title)
                .param("fashionCategoryId", String.valueOf(jdbcTemplate.queryForObject(
                        "SELECT MIN(id) FROM fashion_categories WHERE is_active = true", Long.class)))
                .param("tags", "古着")
                .param("wornItems", "コート：古着屋")
                .param("description", title + "の説明")
                .param("referenceUrl", "https://example.com/item");
        String body = as(author, request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** 投稿の写真の URL（表示順） */
    List<String> postImageUrls(long postId) {
        return jdbcTemplate.queryForList(
                "SELECT image_url FROM post_images WHERE post_id = ? ORDER BY sort_order", String.class, postId);
    }

    /** from が to に相談を申し込み、to が承認した会話の ID を返す */
    long activeConversation(User from, User to) throws Exception {
        String body = as(from, post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\": " + to.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long conversationId = ((Number) JsonPath.read(body, "$.conversationId")).longValue();
        as(to, post("/api/conversations/" + conversationId + "/accept")).andExpect(status().isOk());
        return conversationId;
    }

    /** メッセージを送り、その ID を返す。withImage なら画像も付ける */
    long send(User sender, long conversationId, String text, boolean withImage) throws Exception {
        var request = multipart("/api/conversations/" + conversationId + "/messages").param("body", text);
        if (withImage) {
            request.file(new MockMultipartFile("image", "m.png", "image/png", image(16, 16, "png")));
        }
        String body = as(sender, request).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    // ---- 通報 ----

    ResultActions report(User reporter, String targetType, long targetId, String reason, String comment) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("targetType", targetType);
        request.put("targetId", targetId);
        request.put("reason", reason);
        request.put("comment", comment);
        return as(reporter, post("/api/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(request)));
    }

    /** 対象の通報（未対応のもの）の ID */
    long openCaseId(String targetType, long targetId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM report_cases WHERE target_type = ? AND target_id = ? AND status = 'OPEN'",
                Long.class, targetType, targetId);
    }

    /** 通報の証拠として記録したファイル名（表示順） */
    List<String> evidenceFileNames(long caseId) {
        return jdbcTemplate.queryForList(
                "SELECT file_name FROM report_evidence_files WHERE case_id = ? ORDER BY sort_order", String.class, caseId);
    }

    List<Long> evidenceFileIds(long caseId) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM report_evidence_files WHERE case_id = ? ORDER BY sort_order", Long.class, caseId);
    }

    // ---- 共通 ----

    <B extends AbstractMockHttpServletRequestBuilder<B>> ResultActions as(User user, B request) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(user)));
    }

    ResultActions getAs(User user, String path) throws Exception {
        return as(user, get(path));
    }

    String bearer(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))
                .build();
        return "Bearer " + jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    long count(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    static byte[] image(int width, int height, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** フォルダにあるファイル名の一覧 */
    static Set<String> filesIn(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
