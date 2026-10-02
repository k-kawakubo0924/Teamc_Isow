package com.teamc.isow.backend.image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * アップロードされた画像を検証し、ImageStorage に保存する。
 * 拡張子・MIME タイプは利用者側で自由に偽装できるため、ファイルの中身が実際に画像であることまで確認する。
 */
@Service
public class ImageUploadService {

    /** 1辺の長さの上限（ピクセル）。展開すると巨大になる画像でメモリを使い切られるのを防ぐ */
    static final int MAX_DIMENSION = 10_000;

    /** 画素数の上限（4,000万画素）。同上 */
    static final long MAX_PIXELS = 40_000_000L;

    private static final String INVALID_FORMAT_MESSAGE = "JPEG または PNG の画像を選択してください。";

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

    private final ImageStorage storage;
    private final long maxFileSizeBytes;

    public ImageUploadService(ImageStorage storage, ImageProperties properties) {
        this.storage = storage;
        this.maxFileSizeBytes = properties.maxFileSize().toBytes();
    }

    /**
     * 画像を検証して保存し、ブラウザから表示できる URL を返す（prepare() と store() をまとめて行う）。
     *
     * @throws InvalidImageException 画像の条件を満たさない場合（message は画面にそのまま表示できる）
     */
    public String upload(MultipartFile file) {
        return store(prepare(file));
    }

    /**
     * 画像を検証し、保存できる状態にする（まだ保存はしない）。
     * 複数の画像をまとめて扱う場合に、すべての検証が通ってから保存するために使う。
     *
     * @throws InvalidImageException 画像の条件を満たさない場合（message は画面にそのまま表示できる）
     */
    public PreparedImage prepare(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException("画像を選択してください。");
        }
        // 中身を読み込む前に、申告されたサイズで弾く
        if (file.getSize() > maxFileSizeBytes) {
            throw tooLarge();
        }
        ImageFormat declared = declaredFormat(file);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidImageException("画像を読み込めませんでした。もう一度お試しください。");
        }
        if (content.length > maxFileSizeBytes) {
            throw tooLarge();
        }

        // 拡張子・MIME タイプと、実際の中身の形式が一致すること
        if (ImageFormat.detect(content) != declared) {
            throw new InvalidImageException(INVALID_FORMAT_MESSAGE);
        }
        verifyReadable(content, declared);
        return new PreparedImage(content, declared.extension, declared.mimeType);
    }

    /**
     * prepare() で検証済みの画像を保存し、ブラウザから表示できる URL を返す。
     * ファイル名は元の名前を使わず、推測できない名前（乱数）に実際の形式の拡張子を付けたものにする。
     */
    public String store(PreparedImage image) {
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + image.extension();
        return storage.store(fileName, image.content(), image.mimeType());
    }

    /**
     * 保存済みの画像を削除する（登録が途中で失敗したときの後始末に使う）。
     * 元のエラーを優先して返すため、削除に失敗しても例外は投げず、ログに残す
     */
    public void deleteQuietly(List<String> urls) {
        for (String url : urls) {
            try {
                storage.delete(url);
            } catch (RuntimeException e) {
                log.warn("画像を削除できませんでした（手動で削除が必要）: {}", url, e);
            }
        }
    }

    /**
     * 検証済みの画像（prepare() の戻り値）。
     *
     * @param content 画像の中身
     * @param extension 保存するファイルに付ける拡張子（実際の中身から判定したもの）
     * @param mimeType MIME タイプ（実際の中身から判定したもの）
     */
    public record PreparedImage(byte[] content, String extension, String mimeType) {
    }

    /** 拡張子と MIME タイプが、どちらも同じ対応形式を示していること */
    private static ImageFormat declaredFormat(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        String extension = originalName == null || originalName.lastIndexOf('.') < 0
                ? ""
                : originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        for (ImageFormat format : ImageFormat.values()) {
            if (format.allowedExtensions.contains(extension) && format.mimeType.equals(contentType)) {
                return format;
            }
        }
        throw new InvalidImageException(INVALID_FORMAT_MESSAGE);
    }

    /**
     * 画像として最後まで読み込めること。
     * 先にヘッダーから縦横のサイズだけを読み、大きすぎる画像は全体を読み込む前に弾く
     */
    private static void verifyReadable(byte[] content, ImageFormat format) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format.imageIoName);
        if (!readers.hasNext()) {
            throw new IllegalStateException("画像を読み込む部品がありません: " + format);
        }
        ImageReader reader = readers.next();
        // 一時ファイルを作らず、メモリ上で読み込む
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            if (width <= 0 || height <= 0) {
                throw unreadable();
            }
            if (width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS) {
                throw new InvalidImageException("画像のサイズが大きすぎます。縦横 " + MAX_DIMENSION + " ピクセル以下の画像を選択してください。");
            }
            // 途中で途切れた JPEG は例外にならず警告だけが出るため、警告も不可として扱う
            boolean[] warned = {false};
            reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
            if (reader.read(0) == null || warned[0]) {
                throw unreadable();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidImageException invalid) {
                throw invalid;
            }
            throw unreadable();
        } finally {
            reader.dispose();
        }
    }

    private InvalidImageException tooLarge() {
        return new InvalidImageException("画像は1枚あたり " + (maxFileSizeBytes / 1024 / 1024) + "MB 以下にしてください。");
    }

    private static InvalidImageException unreadable() {
        return new InvalidImageException("画像を読み込めませんでした。壊れていない JPEG または PNG の画像を選択してください。");
    }

    /** 保存を受け付ける画像の形式。先頭のバイト列（マジックナンバー）で中身を判定する */
    enum ImageFormat {
        JPEG("jpg", "image/jpeg", Set.of("jpg", "jpeg"), "jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        PNG("png", "image/png", Set.of("png"), "png", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});

        /** 保存するファイルに付ける拡張子 */
        final String extension;
        final String mimeType;
        /** アップロード時に受け付ける拡張子 */
        final Set<String> allowedExtensions;
        /** ImageIO での形式名 */
        final String imageIoName;
        /** ファイルの先頭のバイト列 */
        private final byte[] magic;

        ImageFormat(String extension, String mimeType, Set<String> allowedExtensions, String imageIoName, byte[] magic) {
            this.extension = extension;
            this.mimeType = mimeType;
            this.allowedExtensions = allowedExtensions;
            this.imageIoName = imageIoName;
            this.magic = magic;
        }

        /** 中身の先頭のバイト列から形式を判定する。対応形式でなければ null */
        static ImageFormat detect(byte[] content) {
            for (ImageFormat format : values()) {
                if (content.length >= format.magic.length
                        && Arrays.equals(content, 0, format.magic.length, format.magic, 0, format.magic.length)) {
                    return format;
                }
            }
            return null;
        }
    }
}
