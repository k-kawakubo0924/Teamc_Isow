package com.teamc.isow.backend.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

/**
 * 保存するときに長辺 2048px に縮めること、受け付ける画素数、縮めるときに使うメモリの確認（docs/post.md「写真の大きさ」）。
 *
 * <ul>
 *   <li>ふつうに撮ったスマホの写真（2,400万画素など）は断らずに受け取り、保存するときに縮める</li>
 *   <li>展開するときに間引いて読み（ImageReadParam.setSourceSubsampling）、元の大きさのまま展開しない。
 *       512MB のサーバーで、大きな写真を受け取ったときにメモリが足りなくならないようにするため</li>
 * </ul>
 * 保存先は使わず、保存された中身をメモリに控えるだけの保存先に差し替えて確かめる
 */
class ImageResizeTest {

    private static final long MAX_FILE_SIZE = DataSize.ofMegabytes(9).toBytes();

    /** 2,400万画素（6000×4000）の写真。生成に時間がかかるため、クラスで1回だけ作る */
    private static byte[] largeJpeg;
    private static byte[] largePngWithAlpha;

    private final RecordingStorage storage = new RecordingStorage();
    private final ImageUploadService service =
            new ImageUploadService(storage, new ImageProperties("local", DataSize.ofMegabytes(9), null));

    @BeforeAll
    static void createLargeImages() {
        largeJpeg = jpeg(photoLike(6000, 4000, BufferedImage.TYPE_3BYTE_BGR), false);
        largePngWithAlpha = ImageTestSupport.encode(photoLike(6000, 4000, BufferedImage.TYPE_4BYTE_ABGR), "png");
    }

    // ---- 縮めて保存する ----

    @Test
    void 長辺が2048を超える横長のJPEGは_長辺2048に縮めて保存する() {
        BufferedImage stored = storeAndDecode(new MockMultipartFile("file", "photo.jpg", "image/jpeg", largeJpeg));

        assertThat(stored.getWidth()).isEqualTo(2048);
        assertThat(stored.getHeight()).isEqualTo(1365);
    }

    @Test
    void 縦長のPNGは_透明な部分を保ったまま長辺2048に縮める() {
        BufferedImage image = new BufferedImage(1500, 4000, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        // 上半分は不透明な赤、下半分は透明
        g.setColor(Color.RED);
        g.fillRect(0, 0, 1500, 2000);
        g.dispose();

        BufferedImage stored = storeAndDecode(
                new MockMultipartFile("file", "tall.png", "image/png", ImageTestSupport.encode(image, "png")));

        assertThat(stored.getWidth()).isEqualTo(768);
        assertThat(stored.getHeight()).isEqualTo(2048);
        assertThat(stored.getColorModel().hasAlpha()).isTrue();
        assertThat(new Color(stored.getRGB(384, 500), true)).isEqualTo(new Color(255, 0, 0, 255));
        assertThat(new Color(stored.getRGB(384, 1500), true).getAlpha()).isZero();
    }

    @Test
    void 縮めても_単色の部分の色は変わらない() {
        byte[] photo = ImageTestSupport.encode(ImageTestSupport.quadrantImage(4000, 3000), "png");

        BufferedImage stored = storeAndDecode(new MockMultipartFile("file", "q.png", "image/png", photo));

        assertThat(stored.getWidth()).isEqualTo(2048);
        assertThat(stored.getHeight()).isEqualTo(1536);
        assertThat(new Color(stored.getRGB(200, 200))).isEqualTo(Color.RED);
        assertThat(new Color(stored.getRGB(1800, 1300))).isEqualTo(Color.BLUE);
    }

    @Test
    void 長辺が2048以下の画像は_大きさを変えず_PNGなら画素も変えない() {
        byte[] png = ImageTestSupport.encode(ImageTestSupport.quadrantImage(2048, 1000), "png");

        storeAndDecode(new MockMultipartFile("file", "same.png", "image/png", png));

        assertThat(ImageTestSupport.pixels(storage.content)).isEqualTo(ImageTestSupport.pixels(png));
    }

    @Test
    void 縦に撮った大きな写真_向き6は_縮めてから縦長に回す() {
        byte[] photo = ImageTestSupport.jpegWithExif(ImageTestSupport.quadrantImage(4000, 3000), 6);

        BufferedImage stored = storeAndDecode(new MockMultipartFile("file", "portrait.jpg", "image/jpeg", photo));

        assertThat(stored.getWidth()).isEqualTo(1536);
        assertThat(stored.getHeight()).isEqualTo(2048);
        // 時計回りに90°回すと、左上の赤は右上に移る
        assertThat(ImageTestSupport.isRed(stored, 1536 - 200, 200)).isTrue();
        assertThat(ImageTestSupport.isBlue(stored, 200, 200)).isTrue();
    }

    @Test
    void 縮めた写真にも_色の情報ICCプロファイルを引き継ぎ_位置情報は残さない() throws Exception {
        byte[] photo = ImageTestSupport.addExif(jpegWithIccProfile(ImageTestSupport.quadrantImage(4000, 3000)), null);
        // テストの前提：元の画像に ICC プロファイルが入っている
        byte[] originalIcc = ImageMetadataStripper.readIccProfile(photo, "jpeg");
        assertThat(originalIcc).isNotNull();

        BufferedImage stored = storeAndDecode(new MockMultipartFile("file", "icc.jpg", "image/jpeg", photo));

        assertThat(stored.getWidth()).isEqualTo(2048);
        assertThat(ImageMetadataStripper.readIccProfile(storage.content, "jpeg")).isEqualTo(originalIcc);
        assertThat(new String(storage.content, java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain(ImageTestSupport.MAKE);
    }

    // ---- 受け付ける画素数 ----

    @Test
    void 今のスマホがふつうに撮る2400万画素の写真は受け付ける() {
        // iPhone 15 以降の標準（24MP。5712×4284）
        byte[] photo = jpeg(photoLike(5712, 4284, BufferedImage.TYPE_3BYTE_BGR), false);

        BufferedImage stored = storeAndDecode(new MockMultipartFile("file", "iphone.jpg", "image/jpeg", photo));

        assertThat(stored.getWidth()).isEqualTo(2048);
    }

    @Test
    void 画素数が6000万を超える画像は断り_どうすればよいかを伝える() throws Exception {
        // 1辺は上限（10,000）以内で、画素数は 6,000万を超える（1ピクセル1ビットのため、ファイルは小さい）
        byte[] huge = ImageTestSupport.encode(new BufferedImage(9000, 6700, BufferedImage.TYPE_BYTE_BINARY), "png");

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "huge.png", "image/png", huge)))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("画像が大きすぎます（6,000万画素まで）。写真アプリなどで画像を小さくしてから、もう一度選択してください。");
    }

    @Test
    void 縦横どちらかが10000ピクセルを超える画像は断り_どうすればよいかを伝える() {
        byte[] wide = ImageTestSupport.encode(new BufferedImage(10_001, 1, BufferedImage.TYPE_BYTE_BINARY), "png");

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "wide.png", "image/png", wide)))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("画像が大きすぎます（縦横それぞれ10,000ピクセルまで）。写真アプリなどで画像を小さくしてから、もう一度選択してください。");
    }

    /**
     * プログレッシブ形式の JPEG は、間引いて読んでも、展開する部品が Java の外で元の大きさぶんのメモリ（1画素あたり約3バイト）を使う。
     * そのため、ふつうの形式より低い上限（2,500万画素）にする。スマホのカメラはふつうの形式で保存するため、写真は影響を受けない
     */
    @Test
    void プログレッシブ形式のJPEGは2500万画素を超えると断る() {
        byte[] progressive = jpeg(new BufferedImage(5001, 5000, BufferedImage.TYPE_BYTE_GRAY), true);

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "p.jpg", "image/jpeg", progressive)))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("画像が大きすぎます（この形式の画像は2,500万画素まで）。写真アプリなどで画像を小さくしてから、もう一度選択してください。");
    }

    @Test
    void プログレッシブ形式のJPEGも_上限以内なら縮めて保存する() {
        byte[] progressive = jpeg(photoLike(3000, 2000, BufferedImage.TYPE_3BYTE_BGR), true);

        BufferedImage stored = storeAndDecode(new MockMultipartFile("file", "p.jpg", "image/jpeg", progressive));

        assertThat(stored.getWidth()).isEqualTo(2048);
        assertThat(stored.getHeight()).isEqualTo(1365);
    }

    // ---- ファイルの大きさ ----

    @Test
    void ファイルが9MBを超える画像は断り_どうすればよいかを伝える() {
        byte[] large = new byte[(int) MAX_FILE_SIZE + 1];

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "large.png", "image/png", large)))
                .isInstanceOf(InvalidImageException.class)
                .hasMessage("写真のファイルが大きすぎます（1枚あたり9MBまで）。写真アプリなどで画像を小さくしてから、もう一度選択してください。");
    }

    // ---- メモリ ----

    /**
     * 2,400万画素の写真を受け取っても、元の大きさのまま展開しないこと。
     * この処理の間にこのスレッドが確保したメモリの合計（捨てたものも数える）が、元の大きさで展開した画素の量より少なければ、
     * 元の大きさの画素を一度も持っていないことになる（同時に持つ量は、確保した合計を超えないため）
     */
    @Test
    void 画素数2400万のJPEGを受け取っても_元の大きさのまま展開しない() {
        assertAllocatedLessThan(new MockMultipartFile("file", "big.jpg", "image/jpeg", largeJpeg), 6000L * 4000 * 3);
    }

    @Test
    void 画素数2400万の透明ありPNGを受け取っても_元の大きさのまま展開しない() {
        assertAllocatedLessThan(new MockMultipartFile("file", "big.png", "image/png", largePngWithAlpha), 6000L * 4000 * 4);
    }

    // ---- 部品 ----

    private BufferedImage storeAndDecode(MockMultipartFile file) {
        service.upload(file);
        return ImageTestSupport.decode(storage.content);
    }

    private void assertAllocatedLessThan(MockMultipartFile file, long fullSizeBytes) {
        // 初回だけの読み込み（クラスの読み込みなど）を測らないよう、先に小さな画像で一度通しておく
        service.prepare(new MockMultipartFile("file", "warm." + (file.getOriginalFilename().endsWith("png") ? "png" : "jpg"),
                file.getContentType(), ImageTestSupport.encode(ImageTestSupport.quadrantImage(3000, 2000),
                        file.getContentType().equals("image/png") ? "png" : "jpg")));

        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = threads.getThreadAllocatedBytes(thread);
        ImageUploadService.PreparedImage prepared = service.prepare(file);
        long allocated = threads.getThreadAllocatedBytes(thread) - before;

        assertThat(prepared.content()).isNotEmpty();
        assertThat(allocated)
                .withFailMessage("""
                        %s の prepare() の間に確保したメモリが %,d バイトで、元の大きさで展開した画素の量（%,d バイト）以上になった。
                        【測っているもの】元の大きさのまま展開していないこと（間引いて読んでいること）。確保した合計（捨てたものも数える）が
                        元の大きさの画素の量より少なければ、元の大きさの画素を一度も持っていない、という理屈で確かめている。
                        【原因は2通り】
                          (1) 本当に使うメモリが増えた（間引いて読む処理が外れた・効かなくなった、縮める処理で大きな配列を作るようになった など）
                          (2) Java の版が変わり、画像の部品が途中で確保する量が増えて、この測り方の基準が動いた（使うメモリ自体は増えていない）
                        【直す前に、どちらかを確かめること】Java が使えるメモリを 48MB に絞って（-Xmx48m）同じ画像の prepare() を通し、
                          最後まで処理できれば (2)、メモリ不足で落ちれば (1)。(1) なら処理を直す。(2) なら理由を docs/post.md「写真の大きさ」に書いてから、
                          この測り方（基準）を見直す。確かめずに数字だけを上げて通さないこと（512MB のサーバーで大きな写真を受け取ると落ちる穴が戻る）""",
                        file.getOriginalFilename(), allocated, fullSizeBytes)
                .isLessThan(fullSizeBytes);
    }

    /** 写真に近い画像（なめらかな色の変化と細い線。JPEG の圧縮で、実際の写真に近い大きさのファイルになる） */
    private static BufferedImage photoLike(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0, 0, Color.RED, width, height, new Color(0, 0, 255, 128)));
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLACK);
        for (int x = 0; x < width; x += 37) {
            g.drawLine(x, 0, width - x, height);
        }
        g.dispose();
        return image;
    }

    private static byte[] jpeg(BufferedImage image, boolean progressive) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (progressive) {
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
        }
        return write(writer, new IIOImage(image, null, null), param);
    }

    /** sRGB 以外の ICC プロファイルを埋め込んだ JPEG（広い色域で撮るスマホの写真を想定） */
    private static byte[] jpegWithIccProfile(BufferedImage image) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_jpeg_image_1.0");
        IIOMetadataNode icc = new IIOMetadataNode("app2ICC");
        icc.setUserObject(ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB));
        root.getElementsByTagName("app0JFIF").item(0).appendChild(icc);
        metadata.setFromTree("javax_imageio_jpeg_image_1.0", root);
        return write(writer, new IIOImage(image, null, metadata), null);
    }

    private static byte[] write(ImageWriter writer, IIOImage image, ImageWriteParam param) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, image, param);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** 保存された中身をメモリに控えるだけの保存先 */
    private static final class RecordingStorage implements ImageStorage {

        private byte[] content;

        @Override
        public String store(String fileName, byte[] content, String contentType) {
            this.content = content;
            return "memory://" + fileName;
        }

        @Override
        public byte[] read(String url) {
            return content;
        }

        @Override
        public void delete(String url) {
            content = null;
        }
    }
}
