package com.teamc.isow.backend.devseed;

import static org.assertj.core.api.Assertions.assertThat;

import com.teamc.isow.backend.auth.AuthService;
import com.teamc.isow.backend.auth.LoginRequest;
import com.teamc.isow.backend.dm.ConsultationService;
import com.teamc.isow.backend.dm.ConsultationUnavailableReason;
import com.teamc.isow.backend.post.TimelineResponse;
import com.teamc.isow.backend.post.TimelineService;
import com.teamc.isow.backend.post.TimelineTab;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 開発用のテストデータの投入（README「開発用のテストデータ」）の確認。
 *
 * <p>投入は起動時に行われるため、有効にした状態でアプリを起動し、できあがったデータを確かめる。
 * 他のテストのデータと混ざらないよう、このテストだけ別のインメモリ DB を使う。
 * ADMIN_EMAIL に投入ユーザーを指定し、最初の起動で管理者になること（起動時の処理の順番）も確かめる
 */
@SpringBootTest(properties = {
    "app.dev-seed.enabled=true",
    "app.admin.email=dev_aoi@example.com",
    "spring.datasource.url=jdbc:h2:mem:devseed;DB_CLOSE_DELAY=-1",
})
class DevDataSeederTest {

    private static final String PASSWORD = "isho1234";

    @Autowired
    private DevDataSeeder seeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private TimelineService timelineService;

    @Autowired
    private ConsultationService consultationService;

    // ---- ユーザー ----

    @Test
    void ユーザーは8人で_メールアドレスはexample_comだけ_全員共通のパスワードでログインできる() {
        List<String> emails = jdbcTemplate.queryForList(
                "SELECT email FROM users WHERE username LIKE 'dev\\_%' ESCAPE '\\' ORDER BY id", String.class);

        assertThat(emails).containsExactly(
                "dev_aoi@example.com", "dev_ren@example.com", "dev_mio@example.com", "dev_sora@example.com",
                "dev_kenta@example.com", "dev_yui@example.com", "dev_haru@example.com", "dev_new@example.com");
        for (String email : emails) {
            assertThat(authService.login(new LoginRequest(email, PASSWORD), "127.0.0.1").token()).isNotBlank();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM users WHERE email NOT LIKE '%@example.com'", Integer.class)).isZero();
    }

    @Test
    void 性別_年代_骨格_パーソナルカラーがばらけ_身長と画像はある人とない人がいる() {
        Map<String, Map<String, Object>> users = users();

        assertThat(users.values().stream().map(u -> u.get("gender")).filter(v -> v != null).distinct())
                .containsExactlyInAnyOrder("MALE", "FEMALE", "OTHER");
        assertThat(users.values().stream().map(u -> u.get("age_group")).filter(v -> v != null).distinct().count())
                .isEqualTo(7);
        assertThat(users.values().stream().map(u -> u.get("body_type")).filter(v -> v != null).distinct())
                .containsExactlyInAnyOrder("ストレート", "ウェーブ", "ナチュラル");
        assertThat(users.values().stream().map(u -> u.get("personal_color")).filter(v -> v != null).distinct())
                .containsExactlyInAnyOrder("イエベ春", "ブルベ夏", "イエベ秋", "ブルベ冬");

        assertThat(users.get("dev_aoi").get("height_cm")).isEqualTo(158);
        assertThat(users.get("dev_mio").get("height_cm")).isNull();
        assertThat(withProfileImage(users)).containsExactlyInAnyOrder("dev_aoi", "dev_ren", "dev_kenta", "dev_yui");

        // プロフィールが一部だけ設定済みの人と、登録しただけの人
        assertThat(users.get("dev_haru").get("gender")).isEqualTo("MALE");
        assertThat(users.get("dev_haru").get("body_type")).isNull();
        assertThat(users.get("dev_new").get("gender")).isNull();
        assertThat(users.get("dev_new").get("display_name")).isEqualTo("dev_new");
        assertThat(users.get("dev_aoi").get("display_name")).isEqualTo("あおい");
    }

    @Test
    void 管理者はこの仕組みでは作らないが_ADMIN_EMAILに投入ユーザーを書けば最初の起動から管理者になる() {
        assertThat(jdbcTemplate.queryForList(
                "SELECT username FROM users WHERE role = 'ADMIN'", String.class)).containsExactly("dev_aoi");
        // 管理者にしたのは ADMIN_EMAIL の処理（システムの操作として記録される）
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM admin_operation_logs WHERE action = 'USER_PROMOTED_TO_ADMIN'", Integer.class))
                .isEqualTo(1);
    }

    // ---- 投稿 ----

    @Test
    void 投稿は10件で_写真の枚数は1枚から10枚まで() {
        List<Integer> imageCounts = jdbcTemplate.queryForList("""
                SELECT (SELECT count(*) FROM post_images i WHERE i.post_id = p.id)
                FROM posts p ORDER BY p.id
                """, Integer.class);

        assertThat(imageCounts).containsExactly(1, 3, 5, 1, 2, 1, 4, 2, 1, 10);
    }

    @Test
    void 着用アイテム_参考情報は空のものと入っているものの両方がある() {
        List<Map<String, Object>> posts = jdbcTemplate.queryForList(
                "SELECT worn_items, reference_url FROM posts ORDER BY id");

        assertThat(posts).extracting(p -> p.get("worn_items") != null)
                .containsExactly(true, true, false, false, false, true, true, true, false, false);
        assertThat(posts).extracting(p -> p.get("reference_url") != null)
                .containsExactly(true, false, true, false, false, false, true, false, false, true);
        assertThat(posts).extracting(p -> (String) p.get("reference_url")).filteredOn(url -> url != null)
                .allSatisfy(url -> assertThat(url).startsWith("https://example.com/"));
    }

    @Test
    void 投稿説明が長いものが1つある() {
        List<Integer> lengths = jdbcTemplate.queryForList(
                "SELECT CHAR_LENGTH(description) FROM posts ORDER BY id", Integer.class);

        assertThat(lengths.stream().filter(length -> length > 1000)).hasSize(1);
        String longest = jdbcTemplate.queryForObject(
                "SELECT description FROM posts ORDER BY CHAR_LENGTH(description) DESC LIMIT 1", String.class);
        assertThat(longest).contains("\n");
        assertThat(longest.length()).isLessThanOrEqualTo(2000);
    }

    @Test
    void 公式タグと手入力のタグの両方が付き_手入力のタグは本番と同じく正規化される() {
        List<Map<String, Object>> tags = jdbcTemplate.queryForList("""
                SELECT DISTINCT t.name, t.is_official FROM post_tags pt JOIN tags t ON t.id = pt.tag_id
                """);

        assertThat(tags).extracting(t -> t.get("is_official")).contains(true, false);
        List<String> userTags = tags.stream().filter(t -> !(Boolean) t.get("is_official"))
                .map(t -> (String) t.get("name")).toList();
        // 「＃Ｙ２Ｋ」と入力したものが、本番と同じ処理で「Y2K」になる
        assertThat(userTags).contains("Y2K", "スニーカー", "ゆるコーデ", "セットアップ", "アメカジ", "ロングコート");
    }

    @Test
    void 写真はその場で作り_本番と同じ保存の経路で保存される() throws Exception {
        List<String> urls = jdbcTemplate.queryForList("""
                SELECT image_url FROM post_images
                UNION ALL SELECT profile_image_url FROM users WHERE profile_image_url IS NOT NULL
                UNION ALL SELECT image_url FROM messages WHERE image_url IS NOT NULL
                """, String.class);

        assertThat(urls).hasSize(30 + 4 + 1);
        for (String url : urls) {
            // 保存の経路（ImageUploadService）が付ける推測できない名前で、テスト用の保存先にある
            assertThat(url).matches("http://localhost/uploads/[0-9a-f]{32}\\.(jpg|png)");
            Path file = Path.of("target/test-uploads").resolve(url.substring(url.lastIndexOf('/') + 1));
            byte[] content = Files.readAllBytes(file);
            assertThat(ImageIO.read(new ByteArrayInputStream(content))).isNotNull();
            assertThat(new String(content, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        }
    }

    // ---- いいね・お気に入り・フォロー ----

    @Test
    void いいね14件_お気に入り5件_フォロー10件() {
        assertThat(count("likes")).isEqualTo(14);
        assertThat(count("favorites")).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM follows WHERE active = true", Integer.class))
                .isEqualTo(10);
        // 相互フォロー（aoi と ren）
        assertThat(follows("dev_aoi", "dev_ren")).isTrue();
        assertThat(follows("dev_ren", "dev_aoi")).isTrue();
        // 登録しただけの人は、誰ともつながっていない
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM follows f JOIN users u ON u.id IN (f.follower_id, f.followee_id)
                WHERE u.username = 'dev_new'
                """, Integer.class)).isZero();
    }

    // ---- 相談（DM） ----

    @Test
    void 相談は申請中_進行中_終了_拒否がそろう() {
        Map<String, String> statuses = jdbcTemplate.queryForList("""
                SELECT r.username || '->' || CASE WHEN c.user1_id = r.id THEN u2.username ELSE u1.username END AS pair,
                       c.status
                FROM conversations c
                JOIN users r ON r.id = c.requested_by_id
                JOIN users u1 ON u1.id = c.user1_id
                JOIN users u2 ON u2.id = c.user2_id
                """).stream().collect(Collectors.toMap(row -> (String) row.get("pair"), row -> (String) row.get("status")));

        assertThat(statuses).containsExactlyInAnyOrderEntriesOf(Map.of(
                "dev_aoi->dev_ren", "ACTIVE",
                "dev_mio->dev_aoi", "ENDED",
                "dev_yui->dev_aoi", "REQUESTED",
                "dev_aoi->dev_sora", "REQUESTED",
                "dev_kenta->dev_aoi", "REJECTED",
                "dev_kenta->dev_ren", "ACTIVE",
                "dev_yui->dev_ren", "ACTIVE"));
    }

    @Test
    void renは受けている相談が上限に達していて_ほかの人からは申し込めない() {
        Long soraId = userId("dev_sora");
        Long renId = userId("dev_ren");

        var status = consultationService.status(String.valueOf(soraId), renId);

        assertThat(status.available()).isFalse();
        assertThat(status.reason()).isEqualTo(ConsultationUnavailableReason.LIMIT_REACHED);
    }

    @Test
    void メッセージは進行中の会話に5通_画像付き1通_最後のrenのメッセージはaoiが未読() {
        List<Map<String, Object>> messages = jdbcTemplate.queryForList("""
                SELECT s.username, m.body, m.image_url, m.read_at FROM messages m
                JOIN users s ON s.id = m.sender_id
                JOIN conversations c ON c.id = m.conversation_id
                JOIN users r ON r.id = c.requested_by_id
                WHERE r.username = 'dev_aoi' AND c.status = 'ACTIVE'
                ORDER BY m.id
                """);

        assertThat(messages).hasSize(5);
        assertThat(messages.stream().filter(m -> m.get("image_url") != null)).hasSize(1);
        Map<String, Object> last = messages.get(4);
        assertThat(last.get("username")).isEqualTo("dev_ren");
        assertThat(last.get("read_at")).isNull();
    }

    @Test
    void 終了した会話は3通で_aoiは読んでいる() {
        List<Map<String, Object>> messages = jdbcTemplate.queryForList("""
                SELECT s.username, m.read_at FROM messages m
                JOIN users s ON s.id = m.sender_id
                JOIN conversations c ON c.id = m.conversation_id
                WHERE c.status = 'ENDED' ORDER BY m.id
                """);

        assertThat(messages).hasSize(3);
        assertThat(messages).filteredOn(m -> m.get("username").equals("dev_mio"))
                .allSatisfy(m -> assertThat(m.get("read_at")).isNotNull());
    }

    // ---- 通知・検索履歴 ----

    @Test
    void aoiには本番と同じ処理で通知が作られ_フォローの通知だけ既読() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT n.type, n.read_at FROM notifications n JOIN users u ON u.id = n.recipient_id
                WHERE u.username = 'dev_aoi'
                """);
        Map<String, Long> byType = rows.stream()
                .collect(Collectors.groupingBy(row -> (String) row.get("type"), Collectors.counting()));

        assertThat(byType).containsExactlyInAnyOrderEntriesOf(Map.of(
                "LIKED", 6L, "FOLLOWED", 4L, "CONSULTATION_REQUESTED", 3L, "CONSULTATION_APPROVED", 1L,
                "MESSAGE_RECEIVED", 2L));
        assertThat(rows).allSatisfy(row -> assertThat(row.get("read_at") != null)
                .isEqualTo(row.get("type").equals("FOLLOWED")));
    }

    @Test
    void ほかの人にも本番と同じく通知が届く() {
        assertThat(notificationTypes("dev_ren")).contains("LIKED", "FOLLOWED", "CONSULTATION_REQUESTED");
        assertThat(notificationTypes("dev_yui")).contains("LIKED");
        assertThat(notificationTypes("dev_kenta")).contains("CONSULTATION_REJECTED");
        assertThat(notificationTypes("dev_mio")).contains("CONSULTATION_APPROVED", "MESSAGE_RECEIVED");
        assertThat(notificationTypes("dev_new")).isEmpty();
    }

    @Test
    void 検索履歴はaoiに5件_renに1件() {
        assertThat(jdbcTemplate.queryForList("""
                SELECT h.keyword FROM search_histories h JOIN users u ON u.id = h.user_id
                WHERE u.username = 'dev_aoi'
                """, String.class)).containsExactlyInAnyOrder("古着", "韓国ファッション", "セットアップ", "ワンピース", "dev_ren");
        assertThat(jdbcTemplate.queryForList("""
                SELECT h.keyword FROM search_histories h JOIN users u ON u.id = h.user_id
                WHERE u.username = 'dev_ren'
                """, String.class)).containsExactly("モノトーン");
    }

    // ---- 並び順（投稿がほぼ同じ時刻になるため） ----

    @Test
    void 投稿がほぼ同じ時刻でも_新着の一覧はページをまたいで重複も抜けもなく同じ順に並ぶ() {
        String subject = String.valueOf(userId("dev_new"));
        List<Long> expected = jdbcTemplate.queryForList("SELECT id FROM posts ORDER BY created_at DESC, id DESC", Long.class);

        for (int round = 0; round < 2; round++) {
            List<Long> paged = new ArrayList<>();
            for (int page = 0; ; page++) {
                TimelineResponse response = timelineService.list(subject, TimelineTab.LATEST, page, 3);
                response.posts().forEach(item -> paged.add(item.id()));
                if (!response.hasNext()) {
                    break;
                }
            }
            assertThat(paged).containsExactlyElementsOf(expected).doesNotHaveDuplicates().hasSize(10);
        }
    }

    // ---- 2回目・途中で失敗した後 ----

    @Test
    void 投入済みなら2回目は何もしない() {
        Map<String, Integer> before = counts();

        seeder.run(null);

        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void 投入済みの印がないのに投入ユーザーがいる場合は_途中で失敗したとみなして何もしない() {
        Map<String, Integer> before = counts();
        jdbcTemplate.update("DELETE FROM master_seed_history WHERE seed_key = ?", DevDataSeeder.SEED_KEY);
        try {
            seeder.run(null);

            assertThat(counts()).isEqualTo(before);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM master_seed_history WHERE seed_key = ?", Integer.class, DevDataSeeder.SEED_KEY))
                    .isZero();
        } finally {
            jdbcTemplate.update("INSERT INTO master_seed_history (seed_key, applied_at) VALUES (?, CURRENT_TIMESTAMP)",
                    DevDataSeeder.SEED_KEY);
        }
    }

    // ---- 部品 ----

    private Map<String, Map<String, Object>> users() {
        return jdbcTemplate.queryForList("""
                SELECT u.username, u.display_name, u.gender, u.age_group, u.height_cm, u.profile_image_url,
                       b.name AS body_type, c.name AS personal_color
                FROM users u
                LEFT JOIN body_types b ON b.id = u.body_type_id
                LEFT JOIN personal_colors c ON c.id = u.personal_color_id
                """).stream().collect(Collectors.toMap(row -> (String) row.get("username"), row -> row));
    }

    private static List<String> withProfileImage(Map<String, Map<String, Object>> users) {
        return users.entrySet().stream().filter(e -> e.getValue().get("profile_image_url") != null)
                .map(Map.Entry::getKey).toList();
    }

    private Long userId(String username) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    private boolean follows(String follower, String followee) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM follows WHERE active = true
                  AND follower_id = (SELECT id FROM users WHERE username = ?)
                  AND followee_id = (SELECT id FROM users WHERE username = ?)
                """, Integer.class, follower, followee) == 1;
    }

    private List<String> notificationTypes(String username) {
        return jdbcTemplate.queryForList("""
                SELECT n.type FROM notifications n JOIN users u ON u.id = n.recipient_id WHERE u.username = ?
                """, String.class, username);
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private Map<String, Integer> counts() {
        return List.of("users", "posts", "post_images", "post_tags", "tags", "likes", "favorites", "follows",
                        "conversations", "messages", "notifications", "search_histories")
                .stream().collect(Collectors.toMap(table -> table, this::count));
    }
}
