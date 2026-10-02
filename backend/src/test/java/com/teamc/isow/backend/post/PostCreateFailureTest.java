package com.teamc.isow.backend.post;

import static com.teamc.isow.backend.post.PostApiTestSupport.image;
import static com.teamc.isow.backend.post.PostApiTestSupport.storedFiles;
import static com.teamc.isow.backend.post.PostApiTestSupport.validRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.image.ImageStorage;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * 投稿作成の途中で失敗したときに、中途半端な状態（画像だけ・タグだけが残る）にならないことの確認。
 * 画像の保存先と投稿の Repository を、指定した回だけ失敗するように差し替えて確かめる（それ以外は本物の処理）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class PostCreateFailureTest {

    /** このテストで新しく作られるはずのタグ（失敗時には残らないこと） */
    private static final String NEW_TAG = "失敗確認用のタグ";

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

    @MockitoSpyBean
    private PostRepository postRepository;

    @Value("${app.image.local.dir}")
    private Path uploadDir;

    private String token;
    private long fashionCategoryId;
    private Set<String> filesBefore;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(new User(PostApiTestSupport.EMAIL, "09000000000", "hash", "post_api_test"));
        token = PostApiTestSupport.token(jwtEncoder, user.getId());
        fashionCategoryId = fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0).getId();
        filesBefore = storedFiles(uploadDir);
    }

    @AfterEach
    void tearDown() {
        PostApiTestSupport.cleanUp(jdbcTemplate);
    }

    @Test
    void 画像の保存が途中で失敗したら_保存済みの画像を削除し_DBにも登録しない() {
        // 1枚目は保存でき、2枚目で失敗する
        doCallRealMethod()
                .doThrow(new UncheckedIOException(new IOException("ディスクがいっぱい")))
                .when(imageStorage).store(anyString(), any(), anyString());

        assertThatThrownBy(() -> mockMvc.perform(requestWithTwoImages())).hasRootCauseInstanceOf(IOException.class);

        verify(imageStorage, times(2)).store(anyString(), any(), anyString());
        assertNothingLeft();
    }

    @Test
    void DBへの登録が失敗したら_保存した画像を削除し_作りかけのタグもロールバックされる() {
        doThrow(new IllegalStateException("DB障害")).when(postRepository).save(any());

        assertThatThrownBy(() -> mockMvc.perform(requestWithTwoImages())).hasRootCauseInstanceOf(IllegalStateException.class);

        // 画像は2枚とも保存されたが、後始末で削除されている
        verify(imageStorage, times(2)).store(anyString(), any(), anyString());
        verify(imageStorage, times(2)).delete(anyString());
        assertNothingLeft();
    }

    @Test
    void タグの作成が他の投稿と衝突した場合は1回だけやり直して登録できる() throws Exception {
        // Repository はインターフェースのため doCallRealMethod() が使えない。
        // 2回目は、スパイの既定の応答（差し替える前の本物に処理を渡す）を使う
        Answer<?> callReal = mockingDetails(postRepository).getMockCreationSettings().getDefaultAnswer();
        doThrow(new DataIntegrityViolationException("一意制約違反"))
                .doAnswer(callReal)
                .when(postRepository).save(any());

        mockMvc.perform(requestWithTwoImages()).andExpect(status().isCreated());

        verify(postRepository, times(2)).save(any());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM posts", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tags WHERE name = ?", Integer.class, NEW_TAG)).isEqualTo(1);
        assertThat(storedFiles(uploadDir)).hasSize(filesBefore.size() + 2);
    }

    @Test
    void やり直しても失敗したら_保存した画像を削除する() {
        doThrow(new DataIntegrityViolationException("一意制約違反")).when(postRepository).save(any());

        assertThatThrownBy(() -> mockMvc.perform(requestWithTwoImages()))
                .hasRootCauseInstanceOf(DataIntegrityViolationException.class);

        verify(postRepository, times(2)).save(any());
        assertNothingLeft();
    }

    private MockMultipartHttpServletRequestBuilder requestWithTwoImages() {
        MockMultipartHttpServletRequestBuilder request = validRequest(token, fashionCategoryId);
        request.file(new MockMultipartFile("images", "2.jpg", "image/jpeg", image(20, 20, "jpg")));
        request.param("tags", NEW_TAG);
        return request;
    }

    private void assertNothingLeft() {
        assertThat(storedFiles(uploadDir)).isEqualTo(filesBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM posts", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM post_images", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tags WHERE name = ?", Integer.class, NEW_TAG)).isZero();
    }
}
