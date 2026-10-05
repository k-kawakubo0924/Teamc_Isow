package com.teamc.isow.backend.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.teamc.isow.backend.common.AgeGroup;
import com.teamc.isow.backend.common.Gender;
import com.teamc.isow.backend.master.BodyType;
import com.teamc.isow.backend.master.BodyTypeRepository;
import com.teamc.isow.backend.master.PersonalColor;
import com.teamc.isow.backend.master.PersonalColorRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** プロフィール編集（PUT /api/users/me・POST /api/users/me/profile-image）の確認 */
@SpringBootTest
@AutoConfigureMockMvc
class ProfileEditApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BodyTypeRepository bodyTypeRepository;

    @Autowired
    private PersonalColorRepository personalColorRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.image.local.dir}")
    private Path imageDir;

    @Value("${app.image.local.public-base-url}")
    private String publicBaseUrl;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private User me;
    private String token;
    private List<BodyType> bodyTypes;
    private List<PersonalColor> personalColors;

    @BeforeEach
    void setUp() {
        me = userRepository.save(new User(ProfileTestSupport.EMAIL_ME, "09000000030", "hash", "profile_me"));
        token = ProfileTestSupport.token(jwtEncoder, me.getId());
        bodyTypes = bodyTypeRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
        personalColors = personalColorRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc();
    }

    @AfterEach
    void tearDown() {
        ProfileTestSupport.cleanUp(jdbcTemplate);
        jdbcTemplate.update("UPDATE body_types SET is_active = true");
    }

    // ---- 認証 ----

    @Test
    void トークンがない場合は401() throws Exception {
        mockMvc.perform(put("/api/users/me").contentType(MediaType.APPLICATION_JSON).content(json(validBody())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/users/me/profile-image").file(png("image")))
                .andExpect(status().isUnauthorized());
    }

    // ---- 項目の更新 ----

    @Test
    void すべての項目を保存し_更新後のプロフィールを返す() throws Exception {
        Map<String, Object> body = validBody();
        body.put("displayName", "  ユウ  ");
        body.put("gender", "MALE");
        body.put("heightCm", 172);
        body.put("ageGroup", "LATE_20S");
        body.put("bodyTypeId", bodyTypes.get(1).getId());
        body.put("personalColorId", personalColors.get(2).getId());

        update(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(me.getId()))
                .andExpect(jsonPath("$.displayName").value("ユウ"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.heightCm").value(172))
                .andExpect(jsonPath("$.ageGroup").value("LATE_20S"))
                .andExpect(jsonPath("$.bodyType.id").value(bodyTypes.get(1).getId()))
                .andExpect(jsonPath("$.bodyType.name").value(bodyTypes.get(1).getName()))
                .andExpect(jsonPath("$.personalColor.id").value(personalColors.get(2).getId()))
                .andExpect(jsonPath("$.me").value(true));

        User saved = userRepository.findWithProfileById(me.getId()).orElseThrow();
        assertThat(saved.getDisplayName()).isEqualTo("ユウ");
        assertThat(saved.getGender()).isEqualTo(Gender.MALE);
        assertThat(saved.getAgeGroup()).isEqualTo(AgeGroup.LATE_20S);
    }

    @Test
    void 名前以外はnullで未設定に戻せる() throws Exception {
        User user = userRepository.findById(me.getId()).orElseThrow();
        user.updateProfile("ユウ", Gender.MALE, 172, AgeGroup.LATE_20S, bodyTypes.get(0), personalColors.get(0));
        userRepository.save(user);

        update(validBody())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").isEmpty())
                .andExpect(jsonPath("$.heightCm").isEmpty())
                .andExpect(jsonPath("$.ageGroup").isEmpty())
                .andExpect(jsonPath("$.bodyType").isEmpty())
                .andExpect(jsonPath("$.personalColor").isEmpty());
    }

    @Test
    void 名前は必須で50文字まで() throws Exception {
        for (Object name : new Object[] {null, "", "   "}) {
            Map<String, Object> body = validBody();
            body.put("displayName", name);
            update(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.displayName").value("名前を入力してください"));
        }
        Map<String, Object> body = validBody();
        body.put("displayName", "あ".repeat(51));
        update(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.displayName").value("名前は50文字以内で入力してください"));

        body.put("displayName", "あ".repeat(50));
        update(body).andExpect(status().isOk());
    }

    @Test
    void 身長は100から250cmまで() throws Exception {
        for (int height : new int[] {99, 251}) {
            Map<String, Object> body = validBody();
            body.put("heightCm", height);
            update(body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.heightCm").value("身長は100〜250cmの範囲で入力してください"));
        }
        for (int height : new int[] {100, 250}) {
            Map<String, Object> body = validBody();
            body.put("heightCm", height);
            update(body).andExpect(status().isOk());
        }
    }

    @Test
    void 存在しない選択肢はエラーになり_何も保存しない() throws Exception {
        Map<String, Object> body = validBody();
        body.put("displayName", "変えたい名前");
        body.put("gender", "UNKNOWN");
        body.put("ageGroup", "TWENTIES");
        body.put("bodyTypeId", 999_999);
        body.put("personalColorId", 999_999);

        update(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.gender").value("性別の選択肢が正しくありません"))
                .andExpect(jsonPath("$.errors.ageGroup").value("年代の選択肢が正しくありません"))
                .andExpect(jsonPath("$.errors.bodyTypeId").value("骨格タイプの選択肢が正しくありません"))
                .andExpect(jsonPath("$.errors.personalColorId").value("パーソナルカラーの選択肢が正しくありません"));

        assertThat(userRepository.findById(me.getId()).orElseThrow().getDisplayName()).isEqualTo("profile_me");
    }

    @Test
    void 非表示にした選択肢は新しく選べないが_設定済みならそのまま保存できる() throws Exception {
        BodyType retired = bodyTypes.get(0);
        Map<String, Object> body = validBody();
        body.put("bodyTypeId", retired.getId());
        update(body).andExpect(status().isOk());

        jdbcTemplate.update("UPDATE body_types SET is_active = false WHERE id = ?", retired.getId());
        // 他の項目だけを変えて保存しても、設定済みの骨格タイプは残る
        body.put("displayName", "名前だけ変更");
        update(body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyType.id").value(retired.getId()));

        // 一度外すと、もう選べない
        body.put("bodyTypeId", null);
        update(body).andExpect(status().isOk());
        body.put("bodyTypeId", retired.getId());
        update(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.bodyTypeId").exists());
    }

    // ---- プロフィール画像 ----

    @Test
    void プロフィール画像を保存し_変更したら前の画像を削除する() throws Exception {
        String first = uploadImage(png("image")).andExpect(status().isOk())
                .andExpect(jsonPath("$.profileImageUrl", startsWith(publicBaseUrl + "/")))
                .andReturn().getResponse().getContentAsString();
        String firstUrl = jsonMapper.readTree(first).get("profileImageUrl").asString();
        assertThat(Files.exists(fileOf(firstUrl))).isTrue();

        String second = uploadImage(png("image")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondUrl = jsonMapper.readTree(second).get("profileImageUrl").asString();

        assertThat(secondUrl).isNotEqualTo(firstUrl);
        assertThat(Files.exists(fileOf(secondUrl))).isTrue();
        assertThat(Files.exists(fileOf(firstUrl))).isFalse();
        assertThat(userRepository.findById(me.getId()).orElseThrow().getProfileImageUrl()).isEqualTo(secondUrl);
        Files.deleteIfExists(fileOf(secondUrl));
    }

    @Test
    void 画像でないファイルや未選択は400で_何も保存しない() throws Exception {
        long before = fileCount();
        uploadImage(new MockMultipartFile("image", "fake.png", "image/png", "not an image".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("JPEG または PNG の画像を選択してください。"));
        mockMvc.perform(multipart("/api/users/me/profile-image").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("画像を選択してください。"));

        assertThat(fileCount()).isEqualTo(before);
        assertThat(userRepository.findById(me.getId()).orElseThrow().getProfileImageUrl()).isNull();
    }

    /** 名前だけを入れ、他は未設定にした内容 */
    private static Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", "ユウ");
        body.put("gender", null);
        body.put("heightCm", null);
        body.put("ageGroup", null);
        body.put("bodyTypeId", null);
        body.put("personalColorId", null);
        return body;
    }

    private ResultActions update(Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/users/me")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    private ResultActions uploadImage(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/users/me/profile-image").file(file)
                .header("Authorization", "Bearer " + token));
    }

    private String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    private static MockMultipartFile png(String name) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "png", out);
        return new MockMultipartFile(name, "icon.png", "image/png", out.toByteArray());
    }

    private Path fileOf(String url) {
        return imageDir.resolve(url.substring(publicBaseUrl.length() + 1));
    }

    private long fileCount() throws IOException {
        if (!Files.isDirectory(imageDir)) {
            return 0;
        }
        try (var files = Files.list(imageDir)) {
            return files.count();
        }
    }
}
