package com.teamc.isow.backend.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import com.jayway.jsonpath.JsonPath;
import com.teamc.isow.backend.dm.Conversation;
import com.teamc.isow.backend.dm.ConversationRepository;
import com.teamc.isow.backend.master.FashionCategoryRepository;
import com.teamc.isow.backend.user.User;
import com.teamc.isow.backend.user.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 投稿の写真・プロフィール画像・DM の画像のすべてで、保存されたファイルから位置情報などが消えていることの確認。
 * スマホの縦写真を想定し、位置情報・撮影日時・端末情報と向き6（時計回りに90°）を書き込んだ JPEG を送る。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImageMetadataApiTest {

    private static final String[] EMAILS = {"exif-a@example.com", "exif-b@example.com"};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private FashionCategoryRepository fashionCategoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Value("${app.image.local.dir}")
    private Path uploadDir;

    private User a;
    private User b;
    /** 横長（40×20）に記録された、向き6の写真。表示すると縦長（20×40） */
    private byte[] photo;

    @BeforeEach
    void setUp() throws Exception {
        a = userRepository.save(new User(EMAILS[0], "09000000110", "hash", "exif_a"));
        b = userRepository.save(new User(EMAILS[1], "09000000111", "hash", "exif_b"));
        photo = ImageTestSupport.jpegWithExif(ImageTestSupport.quadrantImage(40, 20), 6);
        // テストの前提：送る写真から位置情報が読める
        assertThat(metadata(photo).getFirstDirectoryOfType(GpsDirectory.class).getGeoLocation()).isNotNull();
    }

    @AfterEach
    void tearDown() {
        // 通知はユーザー・会話・投稿を参照しているため先に消す
        jdbcTemplate.update("DELETE FROM notifications");
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM post_tags");
        jdbcTemplate.update("DELETE FROM post_images");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM tags WHERE is_official = false");
        jdbcTemplate.update("DELETE FROM users WHERE email IN (?, ?)", (Object[]) EMAILS);
    }

    @Test
    void 投稿の写真から位置情報などが消え_縦向きで保存される() throws Exception {
        String body = mockMvc.perform(multipart("/api/posts")
                        .file(new MockMultipartFile("images", "IMG_0001.jpg", "image/jpeg", photo))
                        .param("title", "縦の写真")
                        .param("fashionCategoryId", String.valueOf(
                                fashionCategoryRepository.findByActiveTrueOrderByDisplayOrderAscIdAsc().get(0).getId()))
                        .param("tags", "古着")
                        .param("description", "説明")
                        .header("Authorization", bearer(a)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertStrippedAndUpright(JsonPath.read(body, "$.imageUrls[0]"));
    }

    @Test
    void プロフィール画像から位置情報などが消え_縦向きで保存される() throws Exception {
        String body = mockMvc.perform(multipart("/api/users/me/profile-image")
                        .file(new MockMultipartFile("image", "IMG_0002.jpg", "image/jpeg", photo))
                        .header("Authorization", bearer(a)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertStrippedAndUpright(JsonPath.read(body, "$.profileImageUrl"));
    }

    @Test
    void DMの画像から位置情報などが消え_縦向きで保存される() throws Exception {
        Conversation conversation = new Conversation(a, b);
        conversation.approve();
        conversationRepository.save(conversation);

        String body = mockMvc.perform(multipart("/api/conversations/" + conversation.getId() + "/messages")
                        .file(new MockMultipartFile("image", "IMG_0003.jpg", "image/jpeg", photo))
                        .header("Authorization", bearer(a)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertStrippedAndUpright(JsonPath.read(body, "$.imageUrl"));
    }

    /** 保存されたファイルに位置情報・撮影日時・端末情報・向きが残っておらず、縦長で左上の赤が右上に移っていること */
    private void assertStrippedAndUpright(String url) throws Exception {
        byte[] stored = Files.readAllBytes(uploadDir.resolve(url.substring(url.lastIndexOf('/') + 1)));

        Metadata metadata = metadata(stored);
        assertThat(metadata.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
        assertThat(metadata.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain(
                "Exif\0\0", ImageTestSupport.MAKE, ImageTestSupport.MODEL, ImageTestSupport.DATE_TIME_ORIGINAL);

        BufferedImage image = ImageTestSupport.decode(stored);
        assertThat(image.getWidth()).isEqualTo(20);
        assertThat(image.getHeight()).isEqualTo(40);
        assertThat(ImageTestSupport.isRed(image, 15, 5)).isTrue();
    }

    private static Metadata metadata(byte[] content) throws Exception {
        return ImageMetadataReader.readMetadata(new ByteArrayInputStream(content), content.length);
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
