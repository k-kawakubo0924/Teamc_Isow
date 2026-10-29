package com.teamc.isow.backend.post;

import static com.teamc.isow.backend.post.PostApiTestSupport.image;
import static com.teamc.isow.backend.post.PostApiTestSupport.storedFiles;
import static com.teamc.isow.backend.post.PostApiTestSupport.validRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.image.ImageStorage;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * 投稿の削除（DELETE /api/posts/{id}。docs/post.md「投稿の削除」）の確認。
 * 投稿は画面と同じ API で作り、写真のファイルも実際に保存してから削除を確かめる。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostDeleteApiTest {

    private static final String OTHER_EMAIL = "post-delete-other@example.com";
    private static final String NEW_TAG = "削除確認用のタグ";

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

    @MockitoSpyBean
    private ImageStorage imageStorage;

    @Value("${app.image.local.dir}")
    private Path uploadDir;

    private String myToken;
    private String otherToken;
    private long fashionCategoryId;

    /** 自分の投稿（写真2枚・手入力のタグ付き。相手がいいね・お気に入りしている） */
    private long myPostId;
    private List<String> myPostUrls;
    /** 相手の投稿（写真1枚。自分がいいね・お気に入りしている） */
    private long otherPostId;
    private List<String> otherPostUrls;

    @BeforeEach
    void setUp() throws Exception {
        User me = userRepository.save(new User(PostApiTestSupport.EMAIL, "09000000000", "hash", "post_delete_me"));
        User other = userRepository.save(new User(OTHER_EMAIL, "09000000001", "hash", "post_delete_other"));
        myToken = PostApiTestSupport.token(jwtEncoder, me.getId());
        otherToken = PostApiTestSupport.token(jwtEncoder, other.getId());
        fashionCategoryId = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0).getId();

        MockMultipartHttpServletRequestBuilder mine = validRequest(myToken, fashionCategoryId);
        mine.file(new MockMultipartFile("images", "2.jpg", "image/jpeg", image(20, 20, "jpg")));
        mine.param("tags", NEW_TAG);
        String myPost = create(mine);
        myPostId = ((Number) JsonPath.read(myPost, "$.id")).longValue();
        myPostUrls = JsonPath.read(myPost, "$.imageUrls");

        String otherPost = create(validRequest(otherToken, fashionCategoryId));
        otherPostId = ((Number) JsonPath.read(otherPost, "$.id")).longValue();
        otherPostUrls = JsonPath.read(otherPost, "$.imageUrls");

        // 相手 → 自分の投稿（いいねの通知が自分に届く）、自分 → 相手の投稿
        react(otherToken, myPostId);
        react(myToken, otherPostId);
    }

    @AfterEach
    void tearDown() {
        PostApiTestSupport.cleanUp(jdbcTemplate);
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", OTHER_EMAIL);
    }

    // ---- 自分の投稿の削除 ----

    @Test
    void 自分の投稿を削除すると204で_投稿と写真の行_タグとのつながり_いいね_お気に入り_通知が消える() throws Exception {
        assertThat(count("SELECT count(*) FROM notifications WHERE post_id = ?", myPostId)).isEqualTo(1);

        deletePost(myToken, myPostId).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM posts WHERE id = ?", myPostId)).isZero();
        assertThat(count("SELECT count(*) FROM post_images WHERE post_id = ?", myPostId)).isZero();
        assertThat(count("SELECT count(*) FROM post_tags WHERE post_id = ?", myPostId)).isZero();
        assertThat(count("SELECT count(*) FROM likes WHERE post_id = ?", myPostId)).isZero();
        assertThat(count("SELECT count(*) FROM favorites WHERE post_id = ?", myPostId)).isZero();
        assertThat(count("SELECT count(*) FROM notifications WHERE post_id = ?", myPostId)).isZero();
    }

    @Test
    void 削除した投稿の写真のファイルだけが消え_他の投稿の写真は残る() throws Exception {
        assertThat(storedFiles(uploadDir)).containsAll(fileNames(myPostUrls)).containsAll(fileNames(otherPostUrls));

        deletePost(myToken, myPostId).andExpect(status().isNoContent());

        Set<String> files = storedFiles(uploadDir);
        assertThat(files).doesNotContainAnyElementsOf(fileNames(myPostUrls));
        assertThat(files).containsAll(fileNames(otherPostUrls));
        // 削除の対象は、post_images に記録されていた URL そのものだけ（1件ずつ、記録の数だけ）
        for (String key : fileNames(myPostUrls)) {
            verify(imageStorage).delete(key);
        }
        verify(imageStorage, times(myPostUrls.size())).delete(anyString());
    }

    @Test
    void 他の投稿と_そのいいね_お気に入り_タグは残る() throws Exception {
        deletePost(myToken, myPostId).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM posts WHERE id = ?", otherPostId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM post_images WHERE post_id = ?", otherPostId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM post_tags WHERE post_id = ?", otherPostId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM likes WHERE post_id = ?", otherPostId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM favorites WHERE post_id = ?", otherPostId)).isEqualTo(1);
        // 手入力で作られたタグは、使う投稿がなくなっても残す（タグの行は消さない）
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tags WHERE name = ?", Integer.class, NEW_TAG))
                .isEqualTo(1);
    }

    @Test
    void 削除した投稿は_詳細もいいねもお気に入りも404になる() throws Exception {
        deletePost(myToken, myPostId).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/posts/" + myPostId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/posts/" + myPostId + "/like").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/posts/" + myPostId + "/favorite").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        // もう一度削除しても 404（画面は削除済みとして扱う）
        deletePost(myToken, myPostId).andExpect(status().isNotFound());
    }

    @Test
    void 写真のファイルの削除に失敗しても_DBの削除は確定し_残りの写真は削除する() throws Exception {
        // 1枚目だけ削除に失敗させる
        doThrow(new UncheckedIOException(new IOException("ファイルを消せない")))
                .when(imageStorage).delete(eq(fileNames(myPostUrls).get(0)));

        deletePost(myToken, myPostId).andExpect(status().isNoContent());

        assertThat(count("SELECT count(*) FROM posts WHERE id = ?", myPostId)).isZero();
        assertThat(count("SELECT count(*) FROM post_images WHERE post_id = ?", myPostId)).isZero();
        Set<String> files = storedFiles(uploadDir);
        assertThat(files).contains(fileNames(myPostUrls).get(0));
        assertThat(files).doesNotContain(fileNames(myPostUrls).get(1));
    }

    // ---- 削除できない場合 ----

    @Test
    void 他人の投稿は403で_何も消えない() throws Exception {
        deletePost(otherToken, myPostId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("この投稿は削除できません。"));

        assertMyPostRemains();
        verify(imageStorage, never()).delete(anyString());
    }

    @Test
    void 存在しない投稿は404() throws Exception {
        deletePost(myToken, 999999).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("投稿が見つかりません。"));
        mockMvc.perform(delete("/api/posts/abc").header("Authorization", "Bearer " + myToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void ログインしていなければ401で_何も消えない() throws Exception {
        mockMvc.perform(delete("/api/posts/" + myPostId)).andExpect(status().isUnauthorized());

        assertMyPostRemains();
        verify(imageStorage, never()).delete(anyString());
    }

    // ---- 自分の投稿かどうか（画面で削除のメニューを出すかの判断に使う） ----

    @Test
    void 詳細のownedByMeは_自分の投稿ならtrue_他人の投稿ならfalse() throws Exception {
        mockMvc.perform(get("/api/posts/" + myPostId).header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.ownedByMe").value(true));
        mockMvc.perform(get("/api/posts/" + myPostId).header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.ownedByMe").value(false));
    }

    @Test
    void 作成直後と自分の投稿一覧のownedByMeはtrue() throws Exception {
        mockMvc.perform(validRequest(myToken, fashionCategoryId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ownedByMe").value(true));
        mockMvc.perform(get("/api/posts/me").header("Authorization", "Bearer " + myToken))
                .andExpect(jsonPath("$.posts[0].ownedByMe").value(true));
    }

    private void assertMyPostRemains() {
        assertThat(count("SELECT count(*) FROM posts WHERE id = ?", myPostId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM post_images WHERE post_id = ?", myPostId)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM post_tags WHERE post_id = ?", myPostId)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM likes WHERE post_id = ?", myPostId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM favorites WHERE post_id = ?", myPostId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM notifications WHERE post_id = ?", myPostId)).isEqualTo(1);
        assertThat(storedFiles(uploadDir)).containsAll(fileNames(myPostUrls));
    }

    private ResultActions deletePost(String token, long postId) throws Exception {
        return mockMvc.perform(delete("/api/posts/" + postId).header("Authorization", "Bearer " + token));
    }

    private String create(MockMultipartHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void react(String token, long postId) throws Exception {
        mockMvc.perform(post("/api/posts/" + postId + "/like").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/posts/" + postId + "/favorite").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private static List<String> fileNames(List<String> urls) {
        return urls.stream().map(url -> url.substring(url.lastIndexOf('/') + 1)).toList();
    }

    private int count(String sql, long id) {
        return jdbcTemplate.queryForObject(sql, Integer.class, id);
    }
}
