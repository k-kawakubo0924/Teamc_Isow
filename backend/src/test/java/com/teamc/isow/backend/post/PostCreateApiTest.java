package com.teamc.isow.backend.post;

import static com.teamc.isow.backend.post.PostApiTestSupport.image;
import static com.teamc.isow.backend.post.PostApiTestSupport.storedFiles;
import static com.teamc.isow.backend.post.PostApiTestSupport.validRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.master.FashionCategory;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * 投稿作成 API（POST /api/posts）の確認。成功時の内容と、入力エラーのときに何も保存されないこと。
 * API がトランザクションを自分で管理するため、テストはロールバックせず、終了時に作ったデータを消す。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostCreateApiTest {

    private static final String INVALID_INPUT_MESSAGE = "入力内容に誤りがあります。赤い欄を修正してください。";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.image.local.dir}")
    private Path uploadDir;

    private User user;
    private String token;
    private FashionCategory kireime;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User(PostApiTestSupport.EMAIL, "09000000000", "hash", "post_api_test"));
        token = PostApiTestSupport.token(jwtEncoder, user.getId());
        kireime = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0);
    }

    @AfterEach
    void tearDown() {
        PostApiTestSupport.cleanUp(jdbcTemplate);
    }

    // ---- 成功 ----

    @Test
    void 投稿でき_写真は送った順に保存され_1枚目がサムネイルになる() throws Exception {
        byte[] png = image(30, 20, "png");
        byte[] jpeg = image(40, 30, "jpg");
        MockMultipartHttpServletRequestBuilder request = multipart("/api/posts");
        request.file(new MockMultipartFile("images", "first.png", "image/png", png));
        request.file(new MockMultipartFile("images", "second.jpg", "image/jpeg", jpeg));
        request.param("title", "  秋の羽織りもの  ")
                .param("fashionCategoryId", String.valueOf(kireime.getId()))
                .param("tags", "古着", " #アウター ", "Y2K", "ｙ２ｋ")
                .param("wornItems", "   ")
                .param("description", "丈が長めのコートなので、\n下は細めにまとめています。")
                .param("referenceUrl", "https://example.com/商品?id=1")
                .header("Authorization", "Bearer " + token);

        String body = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("秋の羽織りもの"))
                .andExpect(jsonPath("$.fashionCategory.id").value(kireime.getId()))
                .andExpect(jsonPath("$.fashionCategory.name").value(kireime.getName()))
                // 表記ゆれ（Y2K / ｙ２ｋ）は1つにまとまり、先に入力した表記が残る
                .andExpect(jsonPath("$.tags[*].name").value(contains("古着", "アウター", "Y2K")))
                .andExpect(jsonPath("$.tags[*].official").value(contains(true, false, false)))
                .andExpect(jsonPath("$.wornItems").value(nullValue()))
                .andExpect(jsonPath("$.description").value("丈が長めのコートなので、\n下は細めにまとめています。"))
                .andExpect(jsonPath("$.referenceUrl").value("https://example.com/商品?id=1"))
                .andExpect(jsonPath("$.imageUrls", hasSize(2)))
                .andExpect(jsonPath("$.imageUrls[0]", startsWith("http://localhost/uploads/")))
                .andExpect(jsonPath("$.author.id").value(user.getId()))
                .andExpect(jsonPath("$.author.username").value("post_api_test"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                // メールアドレスなどの個人情報は返さない
                .andExpect(content().string(not(containsString(PostApiTestSupport.EMAIL))))
                .andReturn().getResponse().getContentAsString();

        // 写真は送った順に保存されている（1枚目が PNG、2枚目が JPEG）
        List<String> imageUrls = JsonPath.read(body, "$.imageUrls");
        assertThat(imageUrls.get(0)).endsWith(".png");
        assertThat(imageUrls.get(1)).endsWith(".jpg");
        assertThat(Files.readAllBytes(uploadDir.resolve(fileName(imageUrls.get(0))))).isEqualTo(png);
        assertThat(Files.readAllBytes(uploadDir.resolve(fileName(imageUrls.get(1))))).isEqualTo(jpeg);
        Integer postId = JsonPath.read(body, "$.id");
        assertThat(jdbcTemplate.queryForList(
                "SELECT sort_order FROM post_images WHERE post_id = ? ORDER BY sort_order", Integer.class, postId))
                .containsExactly(1, 2);
    }

    @Test
    void 手入力のタグは正規化した値で検索し_なければ公式でないタグとして作り_次からは同じタグを使う() throws Exception {
        String first = mockMvc.perform(validRequest(token, kireime.getId()).param("tags", "Korea"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(validRequest(token, kireime.getId()).param("tags", "＃ ｋｏｒｅａ"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // 1件目で作られたタグが、2件目でも使われる（新しい行は作られない）
        Integer firstTagId = JsonPath.read(first, "$.tags[1].id");
        Integer secondTagId = JsonPath.read(second, "$.tags[1].id");
        assertThat(secondTagId).isEqualTo(firstTagId);
        assertThat(JsonPath.<String>read(second, "$.tags[1].name")).isEqualTo("Korea");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_official FROM tags WHERE normalized_name = 'korea'", Boolean.class)).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tags WHERE normalized_name = 'korea'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 同じ名前の手入力タグは_別の投稿でも同じ投稿の中でも1つしか登録されない() throws Exception {
        String first = mockMvc.perform(validRequest(token, kireime.getId()).param("tags", "ワイドパンツ", "ワイドパンツ"))
                .andExpect(status().isCreated())
                // 同じ投稿の中で2回指定しても、付くのは1つ
                .andExpect(jsonPath("$.tags[*].name").value(contains("古着", "ワイドパンツ")))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(validRequest(token, kireime.getId()).param("tags", "ワイドパンツ"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Integer firstTagId = JsonPath.read(first, "$.tags[1].id");
        Integer secondTagId = JsonPath.read(second, "$.tags[1].id");
        assertThat(secondTagId).isEqualTo(firstTagId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM tags WHERE name = 'ワイドパンツ'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT is_official FROM tags WHERE name = 'ワイドパンツ'", Boolean.class)).isFalse();
    }

    @Test
    void 公式タグを指定した場合は既存のタグが使われる() throws Exception {
        Long furugiId = jdbcTemplate.queryForObject("SELECT id FROM tags WHERE name = '古着'", Long.class);
        int tagCount = countTags();

        mockMvc.perform(validRequest(token, kireime.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tags[0].id").value(furugiId))
                .andExpect(jsonPath("$.tags[0].official").value(true));
        assertThat(countTags()).isEqualTo(tagCount);
    }

    // ---- 認証 ----

    @Test
    void ログインしていなければ401で何も保存しない() throws Exception {
        Set<String> filesBefore = storedFiles(uploadDir);

        mockMvc.perform(validRequest(null, kireime.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(validRequest(PostApiTestSupport.token(jwtEncoder, 999_999), kireime.getId()))
                .andExpect(status().isUnauthorized());

        assertNothingSaved(filesBefore);
    }

    // ---- 入力チェック ----

    @Test
    void 必須項目がなければ項目ごとのエラーを返す() throws Exception {
        perform(multipart("/api/posts").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALID_INPUT_MESSAGE))
                .andExpect(jsonPath("$.errors.images").value("写真を1枚以上選択してください"))
                .andExpect(jsonPath("$.errors.title").value("題名を入力してください"))
                .andExpect(jsonPath("$.errors.fashionCategoryId").value("ファッションの種類を選択してください"))
                .andExpect(jsonPath("$.errors.tags").value("タグを1つ以上設定してください"))
                .andExpect(jsonPath("$.errors.description").value("投稿説明を入力してください"))
                .andExpect(jsonPath("$.errors.wornItems").doesNotExist())
                .andExpect(jsonPath("$.errors.referenceUrl").doesNotExist());
    }

    @Test
    void 空白だけの題名と投稿説明は未入力として扱う() throws Exception {
        perform(validRequest(token, kireime.getId(), "title", "   ", "description", "　"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("題名を入力してください"))
                .andExpect(jsonPath("$.errors.description").value("投稿説明を入力してください"));
    }

    @Test
    void 他の項目が正しくても写真が0枚ならエラー() throws Exception {
        perform(multipart("/api/posts")
                        .param("title", "秋の羽織りもの")
                        .param("fashionCategoryId", String.valueOf(kireime.getId()))
                        .param("tags", "古着")
                        .param("description", "説明")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.images").value("写真を1枚以上選択してください"))
                // 写真以外の項目にはエラーが出ない
                .andExpect(jsonPath("$.errors.length()").value(1));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM posts", Integer.class)).isZero();
    }

    @Test
    void 写真は10枚まで() throws Exception {
        MockMultipartHttpServletRequestBuilder request = validRequest(token, kireime.getId());
        for (int i = 2; i <= 11; i++) {
            request.file(new MockMultipartFile("images", i + ".png", "image/png", image(10, 10, "png")));
        }
        Set<String> filesBefore = storedFiles(uploadDir);

        perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.images").value("写真は10枚まで選択できます"));
        assertNothingSaved(filesBefore);
    }

    @Test
    void ブラウザが送る改行の_CRLF_は_LF_にそろえ_1文字として数える() throws Exception {
        // 改行を含めて 2000 文字ちょうど（\r\n を2文字と数えると上限を超える）
        String description = ("あ".repeat(9) + "\r\n").repeat(200);

        mockMvc.perform(validRequest(token, kireime.getId(),
                        "description", description,
                        "wornItems", "アウター：古着\r\nパンツ：スラックス"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value(("あ".repeat(9) + "\n").repeat(200).strip()))
                .andExpect(jsonPath("$.wornItems").value("アウター：古着\nパンツ：スラックス"));
    }

    @Test
    void 文字数の上限を超えるとエラー() throws Exception {
        perform(validRequest(token, kireime.getId(),
                        "title", "あ".repeat(101),
                        "wornItems", "あ".repeat(1001),
                        "description", "あ".repeat(2001)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("題名は100文字以内で入力してください"))
                .andExpect(jsonPath("$.errors.wornItems").value("着用アイテムは1000文字以内で入力してください"))
                .andExpect(jsonPath("$.errors.description").value("投稿説明は2000文字以内で入力してください"));
    }

    @Test
    void 参考情報はhttpかhttpsのURLのみ() throws Exception {
        for (String url : new String[] {"javascript:alert(1)", "ftp://example.com/a", "example.com", "https://", "https://exa mple.com"}) {
            perform(validRequest(token, kireime.getId(), "referenceUrl", url))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.referenceUrl").value("参考情報は http:// または https:// で始まるURLを入力してください"));
        }
    }

    @Test
    void ファッションの種類は存在する有効なものだけ() throws Exception {
        perform(validRequest(token, kireime.getId(), "fashionCategoryId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fashionCategoryId").value("入力内容の形式が正しくありません"));
        perform(validRequest(token, 999_999))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fashionCategoryId").value("ファッションの種類を選択してください"));

        jdbcTemplate.update("UPDATE fashion_categories SET is_active = false WHERE id = ?", kireime.getId());
        perform(validRequest(token, kireime.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fashionCategoryId").value("ファッションの種類を選択してください"));
    }

    /** .param("tags", ...) は既定の「古着」に追加される */
    @Test
    void タグの空_文字数_個数_無効なタグはエラー() throws Exception {
        perform(validRequest(token, kireime.getId()).param("tags", "#"))
                .andExpect(jsonPath("$.errors.tags").value("空のタグは設定できません"));
        perform(validRequest(token, kireime.getId()).param("tags", "あ".repeat(31)))
                .andExpect(jsonPath("$.errors.tags").value("タグは1つ30文字以内で入力してください"));
        perform(validRequest(token, kireime.getId())
                        .param("tags", "t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "t10"))
                .andExpect(jsonPath("$.errors.tags").value("タグは10個まで設定できます"));

        jdbcTemplate.update("UPDATE tags SET is_active = false WHERE name = 'プチプラ'");
        perform(validRequest(token, kireime.getId()).param("tags", "ﾌﾟﾁﾌﾟﾗ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tags").value("「プチプラ」は使用できないタグです"));
    }

    @Test
    void 画像として不正なファイルがあれば何枚目かを示し_他の画像も保存しない() throws Exception {
        MockMultipartHttpServletRequestBuilder request = validRequest(token, kireime.getId());
        request.file(new MockMultipartFile("images", "evil.png", "image/png", "<script>".getBytes()));
        Set<String> filesBefore = storedFiles(uploadDir);
        int tagCount = countTags();

        perform(request.param("tags", "新しいタグ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.images").value("2枚目の写真：JPEG または PNG の画像を選択してください。"));
        assertNothingSaved(filesBefore);
        assertThat(countTags()).isEqualTo(tagCount);
    }

    @Test
    void DBと照らし合わせるエラーと画像のエラーはまとめて返す() throws Exception {
        MockMultipartHttpServletRequestBuilder request = validRequest(token, 999_999);
        request.file(new MockMultipartFile("images", "evil.png", "image/png", "not image".getBytes()));

        perform(request.param("tags", "#"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fashionCategoryId").exists())
                .andExpect(jsonPath("$.errors.tags").exists())
                .andExpect(jsonPath("$.errors.images").exists());
    }

    private ResultActions perform(MockMultipartHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request);
    }

    private void assertNothingSaved(Set<String> filesBefore) {
        assertThat(storedFiles(uploadDir)).isEqualTo(filesBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM posts", Integer.class)).isZero();
    }

    private int countTags() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM tags", Integer.class);
    }

    private static String fileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }
}
