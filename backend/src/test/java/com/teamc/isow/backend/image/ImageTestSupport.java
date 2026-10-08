package com.teamc.isow.backend.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputDirectory;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

/**
 * 画像のテストで共通に使う部品（ImageUploadService・メタデータの削除の確認。投稿・DM・プロフィールのテストからも使う）。
 * 位置情報などを書き込んだ JPEG は Apache Commons Imaging で作る（テストでだけ使うライブラリ）。
 */
public final class ImageTestSupport {

    /** テストの写真に書き込む位置情報（東京タワー付近） */
    public static final double LATITUDE = 35.6586;
    public static final double LONGITUDE = 139.7454;

    /** テストの写真に書き込む端末情報・撮影日時 */
    public static final String MAKE = "TestPhoneMaker";
    public static final String MODEL = "TestPhone 15";
    public static final String DATE_TIME_ORIGINAL = "2026:10:01 12:34:56";

    private ImageTestSupport() {
    }

    /**
     * 左上の4分の1だけ赤く、残りを青く塗った画像（回転・反転したときに、どの角がどこへ移ったかを色で確かめるため）。
     * JPEG の圧縮でも色が崩れないよう、単色の大きな領域にしている
     */
    public static BufferedImage quadrantImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.RED);
        g.fillRect(0, 0, width / 2, height / 2);
        g.dispose();
        return image;
    }

    public static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("書き出せない形式: " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 位置情報・撮影日時・端末情報を書き込んだ JPEG（スマホで撮った写真を想定）。
     *
     * @param orientation 写真の向き（EXIF の Orientation。1〜8）。null なら書き込まない
     */
    public static byte[] jpegWithExif(BufferedImage image, Integer orientation) {
        return addExif(encode(image, "jpg"), orientation);
    }

    /**
     * JPEG に、位置情報・撮影日時・端末情報（と向き）の EXIF を書き込む。EXIF 以外の部分（ICC プロファイルなど）はそのまま残る
     *
     * @param orientation 写真の向き（EXIF の Orientation。1〜8）。null なら書き込まない
     */
    public static byte[] addExif(byte[] jpeg, Integer orientation) {
        try {
            TiffOutputSet exif = new TiffOutputSet();
            exif.setGpsInDegrees(LONGITUDE, LATITUDE);
            TiffOutputDirectory root = exif.getOrCreateRootDirectory();
            root.add(TiffTagConstants.TIFF_TAG_MAKE, MAKE);
            root.add(TiffTagConstants.TIFF_TAG_MODEL, MODEL);
            if (orientation != null) {
                root.add(TiffTagConstants.TIFF_TAG_ORIENTATION, orientation.shortValue());
            }
            exif.getOrCreateExifDirectory().add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, DATE_TIME_ORIGINAL);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new ExifRewriter().updateExifMetadataLossless(jpeg, out, exif);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("EXIF を書き込めませんでした", e);
        }
    }

    public static BufferedImage decode(byte[] content) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IllegalStateException("画像として読めない");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 全画素の色（ARGB）。可逆圧縮の PNG で、保存し直しても画素が変わらないことの確認に使う */
    public static int[] pixels(byte[] content) {
        BufferedImage image = decode(content);
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    /** その画素が赤っぽいか（JPEG の圧縮で少し色がずれても判定できるよう、赤が青より十分に強いかで見る） */
    public static boolean isRed(BufferedImage image, int x, int y) {
        Color color = new Color(image.getRGB(x, y));
        return color.getRed() > 150 && color.getBlue() < 100;
    }

    /** その画素が青っぽいか */
    public static boolean isBlue(BufferedImage image, int x, int y) {
        Color color = new Color(image.getRGB(x, y));
        return color.getBlue() > 150 && color.getRed() < 100;
    }
}
