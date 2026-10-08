package com.teamc.isow.backend.image;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.NodeList;

/**
 * 画像からメタデータを取り除く（docs/post.md「写真のメタデータ」）。
 *
 * <p>展開済みのピクセルから新しいファイルを書き出すことで、元のファイルの付加情報を引き継がない。
 * EXIF（位置情報・撮影日時・端末情報）、XMP・IPTC、埋め込みのサムネイル、コメント、
 * ファイルの後ろに付いた追加データ（スマホの深度マップなど）がすべて消える。
 * 一部だけを切り取る方法は、メーカー独自の書き方や追加データの取りこぼしが起きやすいため使わない。
 *
 * <p>写真の向き（EXIF の Orientation）を消すと、スマホの縦写真が横向きで表示されるため、
 * 先に向きの値どおりにピクセル自体を回転・反転してから書き出す（画素を並べ替えるだけのため、画質は落ちない）。
 *
 * <p>色の情報（ICC プロファイル）だけは元の画像から引き継ぐ。ImageIO は ICC プロファイル付きの画像を、色を変換せずに
 * 元の値のまま展開するため、プロファイルを付け直さないと、広い色域で撮ったスマホの写真の色がくすんで表示される。
 * ICC プロファイルは色の再現のための情報で、位置情報や撮影日時は含まない。
 */
final class ImageMetadataStripper {

    /** JPEG を書き出すときの品質（0〜1）。保存し直す前後の色の差は、平均で 1/255 未満 */
    static final float JPEG_QUALITY = 0.95f;

    /** 向きの値。1 がそのまま、2〜8 が回転・反転（EXIF の規格） */
    private static final int ORIENTATION_NORMAL = 1;

    private static final String JPEG_METADATA_FORMAT = "javax_imageio_jpeg_image_1.0";
    private static final String PNG_METADATA_FORMAT = "javax_imageio_png_1.0";

    private static final Logger log = LoggerFactory.getLogger(ImageMetadataStripper.class);

    private ImageMetadataStripper() {
    }

    /**
     * メタデータを取り除いた画像を返す。
     *
     * @param original アップロードされたファイルの中身（向きと色の情報を読むためだけに使う）
     * @param decoded original を展開したピクセル
     * @param imageIoName 書き出す形式の ImageIO での名前（jpeg / png。元と同じ形式で書き出す）
     * @throws IOException 書き出せなかった場合（呼び出し元で、元の画像を保存せずに断ること）
     */
    static byte[] strip(byte[] original, BufferedImage decoded, String imageIoName) throws IOException {
        BufferedImage upright = applyOrientation(decoded, readOrientation(original));
        return write(upright, imageIoName, readIccProfile(original, imageIoName));
    }

    /**
     * EXIF の向きの値（1〜8）を読む。向きの情報がない・読めない場合は「そのまま（1）」として扱う
     * （写真が横向きになることはあっても、メタデータはこの後すべて消すため、情報は残らない）
     */
    static int readOrientation(byte[] content) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(content), content.length);
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif == null || !exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return ORIENTATION_NORMAL;
            }
            int orientation = exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            return orientation >= 1 && orientation <= 8 ? orientation : ORIENTATION_NORMAL;
        } catch (Exception e) {
            log.warn("画像の向きを読めなかったため、そのままの向きで保存します", e);
            return ORIENTATION_NORMAL;
        }
    }

    /**
     * 向きの値どおりにピクセルを回転・反転する。
     * 色の変換をしないよう、画素の値をそのまま並べ替える（色空間・パレットは元の画像のものを使う）
     */
    static BufferedImage applyOrientation(BufferedImage image, int orientation) {
        if (orientation == ORIENTATION_NORMAL) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        // 向き 5〜8 は90°回転を含むため、縦と横が入れ替わる
        boolean swap = orientation >= 5;
        ColorModel colorModel = image.getColorModel();
        WritableRaster target = colorModel.createCompatibleWritableRaster(swap ? h : w, swap ? w : h);
        Raster source = image.getRaster();
        int[] row = new int[w * source.getNumBands()];
        int bands = source.getNumBands();
        int[] pixel = new int[bands];
        for (int y = 0; y < h; y++) {
            source.getPixels(0, y, w, 1, row);
            for (int x = 0; x < w; x++) {
                // 元の (x, y) を、表示する向きでの (tx, ty) に移す（EXIF の規格の定義どおり）
                int tx;
                int ty;
                switch (orientation) {
                    // 左右反転
                    case 2 -> { tx = w - 1 - x; ty = y; }
                    // 180°回転
                    case 3 -> { tx = w - 1 - x; ty = h - 1 - y; }
                    // 上下反転
                    case 4 -> { tx = x; ty = h - 1 - y; }
                    // 左上と右下を結ぶ線で反転
                    case 5 -> { tx = y; ty = x; }
                    // 時計回りに90°回転。スマホの縦写真で多い
                    case 6 -> { tx = h - 1 - y; ty = x; }
                    // 右上と左下を結ぶ線で反転
                    case 7 -> { tx = h - 1 - y; ty = w - 1 - x; }
                    // 反時計回りに90°回転
                    case 8 -> { tx = y; ty = w - 1 - x; }
                    default -> throw new IllegalArgumentException("向きの値が正しくない: " + orientation);
                }
                System.arraycopy(row, x * bands, pixel, 0, bands);
                target.setPixel(tx, ty, pixel);
            }
        }
        return new BufferedImage(colorModel, target, image.isAlphaPremultiplied(), null);
    }

    /**
     * 元の画像の ICC プロファイル（色の情報）を読む。ない・読めない場合は null
     * （色が少しずれることはあっても、位置情報などが残るわけではないため、保存は続ける）
     */
    static byte[] readIccProfile(byte[] content, String imageIoName) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(imageIoName);
        if (!readers.hasNext()) {
            return null;
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input, true, false);
            IIOMetadata metadata = reader.getImageMetadata(0);
            if ("jpeg".equals(imageIoName)) {
                Object profile = userObject(metadata, JPEG_METADATA_FORMAT, "app2ICC");
                return profile instanceof ICC_Profile icc ? icc.getData() : null;
            }
            Object profile = userObject(metadata, PNG_METADATA_FORMAT, "iCCP");
            return profile instanceof byte[] bytes ? bytes : null;
        } catch (IOException | RuntimeException e) {
            log.warn("画像の色の情報（ICC プロファイル）を読めなかったため、付けずに保存します", e);
            return null;
        } finally {
            reader.dispose();
        }
    }

    private static Object userObject(IIOMetadata metadata, String format, String nodeName) {
        if (metadata == null) {
            return null;
        }
        NodeList nodes = ((IIOMetadataNode) metadata.getAsTree(format)).getElementsByTagName(nodeName);
        return nodes.getLength() == 0 ? null : ((IIOMetadataNode) nodes.item(0)).getUserObject();
    }

    /**
     * 書き出す。メタデータは画像の形式に必要なもの（ImageIO が画像から作る既定値）と、ICC プロファイルだけにする。
     * ICC プロファイルを付けられなかった場合は、付けずに書き出す
     */
    private static byte[] write(BufferedImage image, String imageIoName, byte[] iccProfile) throws IOException {
        if (iccProfile != null) {
            try {
                return writeWith(image, imageIoName, iccProfile);
            } catch (IOException | RuntimeException e) {
                log.warn("色の情報（ICC プロファイル）を付けられなかったため、付けずに保存します", e);
            }
        }
        return writeWith(image, imageIoName, null);
    }

    private static byte[] writeWith(BufferedImage image, String imageIoName, byte[] iccProfile) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(imageIoName);
        if (!writers.hasNext()) {
            throw new IOException("画像を書き出す部品がありません: " + imageIoName);
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if ("jpeg".equals(imageIoName)) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(JPEG_QUALITY);
            }
            IIOMetadata metadata = iccProfile == null ? null : metadataWithIcc(writer, image, param, imageIoName, iccProfile);
            writer.write(null, new IIOImage(image, null, metadata), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    /** 画像から作る既定のメタデータに、ICC プロファイルだけを加える（色の数が画像と合わないプロファイルは付けない） */
    private static IIOMetadata metadataWithIcc(
            ImageWriter writer, BufferedImage image, ImageWriteParam param, String imageIoName, byte[] iccProfile)
            throws IOException {
        ICC_Profile profile = ICC_Profile.getInstance(iccProfile);
        if (profile.getNumComponents() != image.getColorModel().getNumColorComponents()) {
            throw new IOException("ICC プロファイルの色の数が画像と合いません");
        }
        IIOMetadata metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param);
        if ("jpeg".equals(imageIoName)) {
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(JPEG_METADATA_FORMAT);
            NodeList jfif = root.getElementsByTagName("app0JFIF");
            if (jfif.getLength() == 0) {
                throw new IOException("ICC プロファイルを付ける場所（JFIF）がありません");
            }
            IIOMetadataNode icc = new IIOMetadataNode("app2ICC");
            icc.setUserObject(profile);
            jfif.item(0).appendChild(icc);
            metadata.setFromTree(JPEG_METADATA_FORMAT, root);
        } else {
            IIOMetadataNode icc = new IIOMetadataNode("iCCP");
            icc.setAttribute("profileName", "ICC Profile");
            icc.setAttribute("compressionMethod", "deflate");
            icc.setUserObject(iccProfile);
            IIOMetadataNode root = new IIOMetadataNode(PNG_METADATA_FORMAT);
            root.appendChild(icc);
            metadata.mergeTree(PNG_METADATA_FORMAT, root);
        }
        return metadata;
    }
}
