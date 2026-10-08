package com.teamc.isow.backend.image;

import static com.teamc.isow.backend.image.ImageTestSupport.isBlue;
import static com.teamc.isow.backend.image.ImageTestSupport.isRed;
import static org.assertj.core.api.Assertions.assertThat;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.drew.metadata.icc.IccDirectory;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 画像のメタデータの削除（ImageMetadataStripper。docs/post.md「写真のメタデータ」）の確認。
 * 処理の前に位置情報などが読めること（テストの前提）と、処理の後に消えていることを、両方確かめる。
 */
class ImageMetadataStripperTest {

    // ---- 位置情報・撮影日時・端末情報 ----

    @Test
    void JPEGの位置情報_撮影日時_端末情報を取り除く() throws Exception {
        byte[] original = ImageTestSupport.jpegWithExif(ImageTestSupport.quadrantImage(40, 20), null);

        // 処理の前：位置情報・撮影日時・端末情報が読める
        Metadata before = metadata(original);
        GeoLocation location = before.getFirstDirectoryOfType(GpsDirectory.class).getGeoLocation();
        assertThat(location.getLatitude()).isCloseTo(ImageTestSupport.LATITUDE, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(location.getLongitude()).isCloseTo(ImageTestSupport.LONGITUDE, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(before.getFirstDirectoryOfType(ExifIFD0Directory.class).getString(ExifIFD0Directory.TAG_MAKE))
                .isEqualTo(ImageTestSupport.MAKE);
        assertThat(before.getFirstDirectoryOfType(ExifSubIFDDirectory.class)
                .getString(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)).isEqualTo(ImageTestSupport.DATE_TIME_ORIGINAL);

        byte[] stripped = strip(original, "jpeg");

        // 処理の後：EXIF（位置情報・撮影日時・端末情報）がまったく残っていない
        Metadata after = metadata(stripped);
        assertThat(after.getFirstDirectoryOfType(GpsDirectory.class)).isNull();
        assertThat(after.getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
        assertThat(after.getFirstDirectoryOfType(ExifSubIFDDirectory.class)).isNull();
        // バイト列にも、EXIF の見出しと端末名・撮影日時の文字が残っていない
        String raw = new String(stripped, StandardCharsets.ISO_8859_1);
        assertThat(raw).doesNotContain("Exif\0\0", ImageTestSupport.MAKE, ImageTestSupport.MODEL,
                ImageTestSupport.DATE_TIME_ORIGINAL);
        // 画像としては同じ大きさで読める
        BufferedImage image = ImageTestSupport.decode(stripped);
        assertThat(image.getWidth()).isEqualTo(40);
        assertThat(image.getHeight()).isEqualTo(20);
        assertThat(isRed(image, 5, 5)).isTrue();
        assertThat(isBlue(image, 35, 15)).isTrue();
    }

    @Test
    void PNGの埋め込みの文字情報を取り除き_画素は1つも変えない() throws Exception {
        byte[] original = pngWithText("Location", "35.6586,139.7454");
        assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("35.6586,139.7454");

        byte[] stripped = strip(original, "png");

        assertThat(new String(stripped, StandardCharsets.ISO_8859_1)).doesNotContain("Location", "35.6586");
        assertThat(ImageTestSupport.pixels(stripped)).isEqualTo(ImageTestSupport.pixels(original));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"jpeg", "png"})
    void 色の情報_ICCプロファイルは元の画像から引き継ぐ(String format) throws Exception {
        byte[] original = withIccProfile(format);
        // テストの前提：元の画像に ICC プロファイルが入っている
        assertThat(ImageMetadataStripper.readIccProfile(original, format)).isNotNull();

        byte[] stripped = strip(original, format);

        assertThat(ImageMetadataStripper.readIccProfile(stripped, format))
                .isEqualTo(ImageMetadataStripper.readIccProfile(original, format));
        assertThat(metadata(stripped).getFirstDirectoryOfType(IccDirectory.class)).isNotNull();
    }

    @Test
    void 回転した写真にもICCプロファイルを引き継ぐ() throws Exception {
        // ICC プロファイル付きで、向き6（時計回りに90°）の写真
        byte[] withIcc = withIccProfile("jpeg");
        byte[] original = ImageTestSupport.addExif(withIcc, 6);

        byte[] stripped = strip(original, "jpeg");

        assertThat(ImageTestSupport.decode(stripped).getWidth()).isEqualTo(20);
        assertThat(ImageMetadataStripper.readIccProfile(stripped, "jpeg"))
                .isEqualTo(ImageMetadataStripper.readIccProfile(withIcc, "jpeg"));
        assertThat(metadata(stripped).getFirstDirectoryOfType(GpsDirectory.class)).isNull();
    }

    @Test
    void 画質_JPEGは保存し直してもほぼ劣化しない() throws Exception {
        byte[] original = ImageTestSupport.jpegWithExif(ImageTestSupport.quadrantImage(200, 100), null);

        BufferedImage before = ImageTestSupport.decode(original);
        BufferedImage after = ImageTestSupport.decode(strip(original, "jpeg"));

        // 保存し直す前後の色の差の平均（0〜255）。品質 0.95 で書き出すため、ほぼ変わらない。
        // 赤と青の境目のような色が急に変わる部分は、JPEG の色の間引きで1画素ずつは差が出るため、平均で確かめる
        long sum = 0;
        for (int y = 0; y < before.getHeight(); y++) {
            for (int x = 0; x < before.getWidth(); x++) {
                sum += colorDiff(before.getRGB(x, y), after.getRGB(x, y));
            }
        }
        double average = (double) sum / (before.getWidth() * before.getHeight());
        assertThat(average).isLessThan(2.0);
    }

    // ---- 写真の向き ----

    @Test
    void 縦に撮った写真_向き6は時計回りに回して縦長にし_向きの情報も消す() throws Exception {
        // 横長（40×20）に記録され、向き6（時計回りに90°回して表示する）が付いた写真。左上が赤
        byte[] original = ImageTestSupport.jpegWithExif(ImageTestSupport.quadrantImage(40, 20), 6);
        assertThat(ImageMetadataStripper.readOrientation(original)).isEqualTo(6);

        byte[] stripped = strip(original, "jpeg");

        BufferedImage image = ImageTestSupport.decode(stripped);
        // 縦長になり、元の左上（赤）は右上に移る
        assertThat(image.getWidth()).isEqualTo(20);
        assertThat(image.getHeight()).isEqualTo(40);
        assertThat(isRed(image, 15, 5)).isTrue();
        assertThat(isBlue(image, 5, 5)).isTrue();
        assertThat(isBlue(image, 15, 35)).isTrue();
        // 向きの情報も残っていない（ブラウザがさらに回転させないように）
        assertThat(ImageMetadataStripper.readOrientation(stripped)).isEqualTo(1);
        assertThat(metadata(stripped).getFirstDirectoryOfType(ExifIFD0Directory.class)).isNull();
    }

    /** 元の左上（赤）が、表示したときにどの位置に来るか */
    @ParameterizedTest(name = "向き{0}")
    @CsvSource({
        // 向き, 幅, 高さ, 赤になる位置 x, y
        "1, 40, 20, 5, 5",
        "3, 40, 20, 35, 15",
        "6, 20, 40, 15, 5",
        "8, 20, 40, 5, 35",
    })
    void JPEGの写真の向きどおりに回転してから保存する(int orientation, int width, int height, int redX, int redY)
            throws Exception {
        byte[] original = ImageTestSupport.jpegWithExif(ImageTestSupport.quadrantImage(40, 20), orientation);

        BufferedImage image = ImageTestSupport.decode(strip(original, "jpeg"));

        assertThat(image.getWidth()).isEqualTo(width);
        assertThat(image.getHeight()).isEqualTo(height);
        assertThat(isRed(image, redX, redY)).isTrue();
    }

    /**
     * 8通りの向きすべてで、四隅の画素がどこへ移るかを確かめる（EXIF の規格どおり）。
     * 元の画像（幅3×高さ2）の四隅を 左上=1・右上=2・左下=3・右下=4 とし、表示したときの四隅を 左上・右上・左下・右下 の順に並べる
     */
    @ParameterizedTest(name = "向き{0}")
    @CsvSource({
        "1, 3, 2, 1-2-3-4",
        // 左右反転
        "2, 3, 2, 2-1-4-3",
        // 180°回転
        "3, 3, 2, 4-3-2-1",
        // 上下反転
        "4, 3, 2, 3-4-1-2",
        // 左上と右下を結ぶ線で反転
        "5, 2, 3, 1-3-2-4",
        // 時計回りに90°
        "6, 2, 3, 3-1-4-2",
        // 右上と左下を結ぶ線で反転
        "7, 2, 3, 4-2-3-1",
        // 反時計回りに90°
        "8, 2, 3, 2-4-1-3",
    })
    void 向きの8通りすべてで四隅が規格どおりに移る(int orientation, int width, int height, String corners) {
        BufferedImage source = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, 1);
        source.setRGB(2, 0, 2);
        source.setRGB(0, 1, 3);
        source.setRGB(2, 1, 4);

        BufferedImage result = ImageMetadataStripper.applyOrientation(source, orientation);

        assertThat(result.getWidth()).isEqualTo(width);
        assertThat(result.getHeight()).isEqualTo(height);
        int right = width - 1;
        int bottom = height - 1;
        String actual = String.join("-", List.of(
                corner(result, 0, 0), corner(result, right, 0), corner(result, 0, bottom), corner(result, right, bottom)));
        assertThat(actual).isEqualTo(corners);
    }

    @Test
    void 向きの情報が壊れていても_そのままの向きとして扱い保存を続ける() {
        // EXIF の見出しはあるが、中身が壊れている JPEG
        byte[] jpeg = ImageTestSupport.encode(ImageTestSupport.quadrantImage(40, 20), "jpg");
        byte[] broken = new byte[jpeg.length + 12];
        broken[0] = (byte) 0xFF;
        broken[1] = (byte) 0xD8;
        broken[2] = (byte) 0xFF;
        broken[3] = (byte) 0xE1;
        broken[4] = 0;
        broken[5] = 10;
        System.arraycopy("Exif\0\0ZZZZ".getBytes(StandardCharsets.ISO_8859_1), 0, broken, 6, 8);
        System.arraycopy(jpeg, 2, broken, 14, jpeg.length - 2);

        assertThat(ImageMetadataStripper.readOrientation(broken)).isEqualTo(1);
    }

    // ---- 部品 ----

    private static byte[] strip(byte[] original, String imageIoName) throws Exception {
        return ImageMetadataStripper.strip(original, ImageTestSupport.decode(original), imageIoName);
    }

    private static Metadata metadata(byte[] content) throws Exception {
        return ImageMetadataReader.readMetadata(new ByteArrayInputStream(content), content.length);
    }

    private static String corner(BufferedImage image, int x, int y) {
        return String.valueOf(image.getRGB(x, y) & 0xFFFFFF);
    }

    private static int colorDiff(int a, int b) {
        int diff = 0;
        for (int shift = 0; shift <= 16; shift += 8) {
            diff = Math.max(diff, Math.abs(((a >> shift) & 0xFF) - ((b >> shift) & 0xFF)));
        }
        return diff;
    }

    /** 文字情報（tEXt）を埋め込んだ PNG（位置情報を文字で書き込むアプリを想定） */
    private static byte[] pngWithText(String key, String value) throws Exception {
        BufferedImage image = ImageTestSupport.quadrantImage(30, 20);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
        IIOMetadataNode entry = new IIOMetadataNode("tEXtEntry");
        entry.setAttribute("keyword", key);
        entry.setAttribute("value", value);
        IIOMetadataNode text = new IIOMetadataNode("tEXt");
        text.appendChild(entry);
        IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
        root.appendChild(text);
        metadata.mergeTree("javax_imageio_png_1.0", root);
        return write(writer, new IIOImage(image, null, metadata));
    }

    /**
     * sRGB 以外の ICC プロファイルを埋め込んだ画像（広い色域で撮るスマホの写真を想定。40×20、左上が赤）。
     * ImageIO は画像の色空間から ICC プロファイルを自動では付けないため、メタデータに直接入れる
     */
    private static byte[] withIccProfile(String format) throws Exception {
        BufferedImage image = ImageTestSupport.quadrantImage(40, 20);
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), null);
        ICC_Profile profile = ICC_Profile.getInstance(ColorSpace.CS_LINEAR_RGB);
        if ("jpeg".equals(format)) {
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_jpeg_image_1.0");
            IIOMetadataNode icc = new IIOMetadataNode("app2ICC");
            icc.setUserObject(profile);
            root.getElementsByTagName("app0JFIF").item(0).appendChild(icc);
            metadata.setFromTree("javax_imageio_jpeg_image_1.0", root);
        } else {
            IIOMetadataNode icc = new IIOMetadataNode("iCCP");
            icc.setAttribute("profileName", "test");
            icc.setAttribute("compressionMethod", "deflate");
            icc.setUserObject(profile.getData());
            IIOMetadataNode root = new IIOMetadataNode("javax_imageio_png_1.0");
            root.appendChild(icc);
            metadata.mergeTree("javax_imageio_png_1.0", root);
        }
        return write(writer, new IIOImage(image, null, metadata));
    }

    private static byte[] write(ImageWriter writer, IIOImage image) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(image);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

}
