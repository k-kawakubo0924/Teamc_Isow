package com.teamc.isow.backend.devseed;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 開発用のテストデータの写真を、その場で作る（リポジトリに画像ファイルを置かないため）。
 * 単色の背景に、誰の何枚目の写真かを英数字で描いただけの画像。
 * 日本語は、字体のない環境（Docker など）で文字化けするため描かない。
 *
 * <p>作った画像は MultipartFile として、画面からのアップロードと同じ保存の経路（ImageUploadService.prepare()）に渡す
 */
final class DevSeedImages {

    /** 写真の縦横の比率（表示の切り抜きを確かめられるよう、縦長・正方形・横長を混ぜる） */
    enum Shape {
        PORTRAIT(1080, 1350),
        SQUARE(1080, 1080),
        LANDSCAPE(1200, 900);

        private final int width;
        private final int height;

        Shape(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }

    /** 背景の色（落ち着いた色を順に使う） */
    private static final Color[] COLORS = {
        new Color(0xC9, 0xB8, 0xA6), new Color(0x9F, 0xB4, 0xC7), new Color(0xB7, 0xC9, 0xA8),
        new Color(0xD8, 0xA7, 0xA0), new Color(0xA8, 0xA0, 0xC4), new Color(0xE0, 0xC9, 0x8F),
        new Color(0x8F, 0xA8, 0xA0), new Color(0xC4, 0x9F, 0xB4),
    };

    private DevSeedImages() {
    }

    /**
     * 投稿の写真（JPEG）
     *
     * @param label 画像に描く文字（例：「dev_ren P3」）
     * @param index 何枚目か（1から）
     * @param total 全部で何枚か
     */
    static MultipartFile postPhoto(String label, int index, int total, Shape shape, int colorIndex) {
        BufferedImage image = draw(shape.width, shape.height, COLORS[Math.floorMod(colorIndex, COLORS.length)],
                label, index + " / " + total);
        return new GeneratedImageFile("photo.jpg", "image/jpeg", encode(image, "jpeg"));
    }

    /** プロフィール画像（PNG。投稿の写真とあわせて、両方の形式が保存の経路を通るようにする） */
    static MultipartFile profileImage(String username, int colorIndex) {
        BufferedImage image = draw(400, 400, COLORS[Math.floorMod(colorIndex, COLORS.length)], username, "");
        return new GeneratedImageFile("profile.png", "image/png", encode(image, "png"));
    }

    /** DM で送る画像（JPEG） */
    static MultipartFile messageImage(String label) {
        BufferedImage image = draw(800, 800, COLORS[COLORS.length - 1], label, "DM");
        return new GeneratedImageFile("message.jpg", "image/jpeg", encode(image, "jpeg"));
    }

    private static BufferedImage draw(int width, int height, Color background, String line1, String line2) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(background);
            g.fillRect(0, 0, width, height);
            g.setColor(Color.WHITE);
            drawCentered(g, line1, new Font(Font.SANS_SERIF, Font.BOLD, width / 12), width, height / 2);
            if (!line2.isEmpty()) {
                drawCentered(g, line2, new Font(Font.SANS_SERIF, Font.PLAIN, width / 16), width, height / 2 + width / 8);
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static void drawCentered(Graphics2D g, String text, Font font, int width, int baseline) {
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, (width - metrics.stringWidth(text)) / 2, baseline);
    }

    private static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, format, out)) {
                throw new IllegalStateException("画像を作れませんでした: " + format);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * その場で作った画像を、画面から送られたファイルと同じ形（MultipartFile）で渡すための入れ物。
     * Spring の MockMultipartFile はテスト用の部品で、本体のコードからは使えないため用意する
     */
    private record GeneratedImageFile(String originalFilename, String contentType, byte[] content)
            implements MultipartFile {

        @Override
        public String getName() {
            return "image";
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content.clone();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }

        @Override
        public void transferTo(Path dest) throws IOException {
            Files.copy(getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
