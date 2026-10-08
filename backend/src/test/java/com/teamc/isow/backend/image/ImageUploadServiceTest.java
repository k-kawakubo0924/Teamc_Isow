package com.teamc.isow.backend.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 画像の検証・保存・配信の確認。
 * 保存先は src/test/resources/application.properties の target/test-uploads。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImageUploadServiceTest {

    private static final String BASE_URL = "http://localhost/uploads/";

    @Autowired
    private ImageUploadService imageUploadService;

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.image.local.dir}")
    private Path uploadDir;

    // ---- 保存できるもの ----

    @Test
    void PNGは推測できない名前で保存され_表示用のURLが返る() throws Exception {
        byte[] png = image(20, 10, "png");

        String url = imageUploadService.upload(new MockMultipartFile("file", "my photo.png", "image/png", png));

        assertThat(url).startsWith(BASE_URL).doesNotContain("my photo");
        String fileName = url.substring(BASE_URL.length());
        // 元のファイル名ではなく、乱数（16進数32桁）＋実際の形式の拡張子
        assertThat(fileName).matches("[0-9a-f]{32}\\.png");
        // メタデータを取り除くために保存し直すため、バイト列は変わるが、PNG は画素が1つも変わらない
        assertThat(ImageTestSupport.pixels(Files.readAllBytes(uploadDir.resolve(fileName))))
                .isEqualTo(ImageTestSupport.pixels(png));
    }

    @Test
    void JPEGは拡張子jpegでも受け付け_保存時の拡張子はjpgになる() throws Exception {
        String url = imageUploadService.upload(new MockMultipartFile("file", "photo.JPEG", "image/jpeg", image(20, 10, "jpg")));

        assertThat(url).matches("http://localhost/uploads/[0-9a-f]{32}\\.jpg");
    }

    @Test
    void 同じ画像を2回保存しても別の名前になる() throws Exception {
        byte[] png = image(10, 10, "png");

        String first = imageUploadService.upload(new MockMultipartFile("file", "a.png", "image/png", png));
        String second = imageUploadService.upload(new MockMultipartFile("file", "a.png", "image/png", png));

        assertThat(first).isNotEqualTo(second);
    }

    // ---- 形式の検証（拡張子・MIME タイプ・中身） ----

    @Test
    void 中身が画像でなければ_拡張子とMIMEタイプが画像でも保存しない() {
        byte[] text = "<script>alert(1)</script>".getBytes();

        assertRejected(new MockMultipartFile("file", "evil.png", "image/png", text));
    }

    @Test
    void 先頭だけ画像に見せかけたファイルは保存しない() throws Exception {
        byte[] fake = Arrays.copyOf(image(10, 10, "png"), 8);

        assertRejected(new MockMultipartFile("file", "fake.png", "image/png", fake));
    }

    @Test
    void 拡張子やMIMEタイプが中身の形式と一致しなければ保存しない() throws Exception {
        byte[] png = image(10, 10, "png");

        assertRejected(new MockMultipartFile("file", "photo.jpg", "image/jpeg", png));
        assertRejected(new MockMultipartFile("file", "photo.png", "image/jpeg", png));
        assertRejected(new MockMultipartFile("file", "photo.png", "text/plain", png));
        assertRejected(new MockMultipartFile("file", "photo", "image/png", png));
    }

    @Test
    void 対応していない形式は保存しない() throws Exception {
        assertRejected(new MockMultipartFile("file", "anim.gif", "image/gif", image(10, 10, "gif")));
    }

    @Test
    void 途中で途切れた画像は保存しない() throws Exception {
        byte[] png = image(200, 200, "png");
        byte[] jpeg = image(200, 200, "jpg");

        assertRejected(new MockMultipartFile("file", "broken.png", "image/png", Arrays.copyOf(png, png.length / 2)));
        assertRejected(new MockMultipartFile("file", "broken.jpg", "image/jpeg", Arrays.copyOf(jpeg, jpeg.length / 2)));
    }

    // ---- サイズの検証 ----

    @Test
    void 空のファイルは保存しない() {
        assertRejected(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]));
    }

    @Test
    void サイズ上限の10MBを超えるファイルは保存しない() {
        byte[] large = new byte[10 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> imageUploadService.upload(new MockMultipartFile("file", "large.png", "image/png", large)))
                .isInstanceOf(InvalidImageException.class)
                .hasMessageContaining("10MB");
    }

    @Test
    void 縦横が大きすぎる画像はファイルが小さくても保存しない() throws Exception {
        // 1辺の上限を超える（ファイル自体は数KB）
        assertRejected(new MockMultipartFile("file", "wide.png", "image/png", binaryImage(ImageUploadService.MAX_DIMENSION + 1, 1)));
        // 1辺は上限以内だが、画素数の上限（4,000万）を超える
        assertRejected(new MockMultipartFile("file", "huge.png", "image/png", binaryImage(7000, 6000)));
    }

    // ---- 配信 ----

    @Test
    void 保存した画像はログインなしでブラウザから表示でき_形式の推測を禁止するヘッダーが付く() throws Exception {
        byte[] png = image(10, 10, "png");
        String url = imageUploadService.upload(new MockMultipartFile("file", "a.png", "image/png", png));

        mockMvc.perform(get("/uploads/" + url.substring(BASE_URL.length())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(png));
    }

    @Test
    void 存在しない画像は404_GET以外は認証が必要() throws Exception {
        mockMvc.perform(get("/uploads/0123456789abcdef0123456789abcdef.png")).andExpect(status().isNotFound());
        mockMvc.perform(post("/uploads/0123456789abcdef0123456789abcdef.png")).andExpect(status().isUnauthorized());
    }

    // ---- メタデータの削除に失敗した場合 ----

    @Test
    void メタデータを取り除けなかった画像は_元の画像を保存せずに断る() throws Exception {
        byte[] photo = ImageTestSupport.jpegWithExif(ImageTestSupport.quadrantImage(40, 20), null);
        long filesBefore = countStoredFiles();

        try (MockedStatic<ImageMetadataStripper> stripper = mockStatic(ImageMetadataStripper.class)) {
            stripper.when(() -> ImageMetadataStripper.strip(any(), any(), any()))
                    .thenThrow(new IOException("書き出しに失敗（テスト）"));

            assertThatThrownBy(() -> imageUploadService.upload(
                    new MockMultipartFile("file", "photo.jpg", "image/jpeg", photo)))
                    .isInstanceOf(InvalidImageException.class)
                    .hasMessage("画像を処理できませんでした。別の画像を選択してください。");
        }

        // 位置情報が付いたままの元の画像は、保存されていない
        assertThat(countStoredFiles()).isEqualTo(filesBefore);
    }

    private long countStoredFiles() throws IOException {
        if (!Files.exists(uploadDir)) {
            return 0;
        }
        try (var files = Files.list(uploadDir)) {
            return files.count();
        }
    }

    private void assertRejected(MockMultipartFile file) {
        assertThatThrownBy(() -> imageUploadService.upload(file)).isInstanceOf(InvalidImageException.class);
    }

    private static byte[] image(int width, int height, String format) throws IOException {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format);
    }

    /** 1ピクセル1ビットの画像（大きな縦横でもテスト中のメモリをあまり使わない） */
    private static byte[] binaryImage(int width, int height) throws IOException {
        return encode(new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY), "png");
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
