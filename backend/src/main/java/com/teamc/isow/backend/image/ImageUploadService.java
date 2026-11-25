package com.teamc.isow.backend.image;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * アップロードされた画像を検証し、ImageStorage に保存する（投稿の写真・プロフィール画像・DM の画像で共通）。
 * 拡張子・MIME タイプは利用者側で自由に偽装できるため、ファイルの中身が実際に画像であることまで確認する。
 * 保存する前に、位置情報などのメタデータを取り除く（ImageMetadataStripper）。
 */
@Service
public class ImageUploadService {

    /**
     * 1辺の長さの上限（ピクセル）。展開すると巨大になる画像で、処理の時間とメモリを使い切られるのを防ぐ
     * （docs/post.md「写真の大きさ」）
     */
    static final int MAX_DIMENSION = 10_000;

    /**
     * 画素数の上限（6,000万画素）。今のスマホがふつうに撮る写真（2,400万画素）と、高画素のモード（5,000万画素前後）が通る大きさ。
     * 展開するときは間引いて読むため、使うメモリは元の画素数に比例しない
     */
    static final long MAX_PIXELS = 60_000_000L;

    /**
     * プログレッシブ形式の JPEG の画素数の上限（2,500万画素）。この形式は、間引いて読んでも、展開する部品が Java の外で
     * 元の大きさぶんのメモリ（1画素あたり約3バイト）を使うため、低くしている。スマホのカメラはふつうの形式で保存する
     */
    static final long MAX_PROGRESSIVE_JPEG_PIXELS = 25_000_000L;

    /** 保存するときの長辺の上限（ピクセル）。これより大きい画像は縮めて保存する */
    static final int MAX_LONG_SIDE = 2048;

    private static final String INVALID_FORMAT_MESSAGE = "JPEG または PNG の画像を選択してください。";

    /** 大きすぎる画像を断るときに、どうすればよいかを伝える文 */
    private static final String SHRINK_HINT = "写真アプリなどで画像を小さくしてから、もう一度選択してください。";

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

    private final ImageStorage storage;
    private final long maxFileSizeBytes;

    public ImageUploadService(ImageStorage storage, ImageProperties properties) {
        this.storage = storage;
        this.maxFileSizeBytes = properties.maxFileSize().toBytes();
    }

    /**
     * 画像を検証して保存し、キーを返す（prepare() と store() をまとめて行う。URL は ImageUrls で組み立てる）。
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
        // 長辺が MAX_LONG_SIDE を超える画像は縮める（向きを直す前に縮め、回す画素を少なくする）
        BufferedImage image = ImageResizer.fitWithin(decode(content, declared), MAX_LONG_SIDE);

        // 位置情報・撮影日時・端末情報などのメタデータを取り除く（写真の向きはピクセルを回転して保つ）。
        // 取り除けなかった場合は、メタデータが付いたまま公開されないよう、元の画像を保存せずに断る
        byte[] stripped;
        try {
            stripped = ImageMetadataStripper.strip(content, image, declared.imageIoName);
        } catch (IOException | RuntimeException e) {
            log.warn("画像のメタデータを取り除けませんでした", e);
            throw new InvalidImageException("画像を処理できませんでした。別の画像を選択してください。");
        }
        return new PreparedImage(stripped, declared.extension, declared.mimeType);
    }

    /**
     * prepare() で検証済みの画像を保存し、キーを返す（DB にはこのキーだけを保存する）。
     * ファイル名は元の名前を使わず、推測できない名前（乱数）に実際の形式の拡張子を付けたものにする。
     */
    public String store(PreparedImage image) {
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + image.extension();
        return storage.store(fileName, image.content(), image.mimeType());
    }

    /**
     * 保存済みの画像を、渡されたキーごとに1件ずつ削除する
     * （登録が途中で失敗したときの後始末、プロフィール画像の差し替え、投稿の削除に使う）。
     * 元の処理の結果を優先するため、削除に失敗しても例外は投げず、ログに残して残りの削除を続ける
     */
    public void deleteQuietly(List<String> keys) {
        for (String key : keys) {
            try {
                storage.delete(key);
            } catch (RuntimeException e) {
                log.warn("画像を削除できませんでした（手動で削除が必要）: {}", key, e);
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
     * 画像として最後まで読み込めることを確かめ、展開したピクセルを返す（縮めて、メタデータを取り除くのにそのまま使う）。
     * 先にヘッダーから縦横のサイズだけを読み、大きすぎる画像は全体を読み込む前に弾く。
     *
     * <p>長辺が MAX_LONG_SIDE の2倍以上ある画像は、展開するときに間引いて読む（ImageReadParam.setSourceSubsampling）。
     * 元の大きさの画素を一度も持たないため、大きな写真でもメモリを使い切らない。間引くのは、長辺が MAX_LONG_SIDE を下回らない
     * ところまで（細かい柄が崩れないよう、最後の縮小は面積平均で行う。ImageResizer）
     */
    private static BufferedImage decode(byte[] content, ImageFormat format) {
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
            if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                throw new InvalidImageException("画像が大きすぎます（縦横それぞれ10,000ピクセルまで）。" + SHRINK_HINT);
            }
            long pixels = (long) width * height;
            if (pixels > MAX_PIXELS) {
                throw new InvalidImageException("画像が大きすぎます（6,000万画素まで）。" + SHRINK_HINT);
            }
            if (format == ImageFormat.JPEG && pixels > MAX_PROGRESSIVE_JPEG_PIXELS && isProgressiveJpeg(content)) {
                throw new InvalidImageException("画像が大きすぎます（この形式の画像は2,500万画素まで）。" + SHRINK_HINT);
            }
            ImageReadParam param = reader.getDefaultReadParam();
            int step = Math.max(1, Math.max(width, height) / MAX_LONG_SIDE);
            param.setSourceSubsampling(step, step, 0, 0);
            // 途中で途切れた JPEG は例外にならず警告だけが出るため、警告も不可として扱う
            boolean[] warned = {false};
            reader.addIIOReadWarningListener((source, warning) -> warned[0] = true);
            BufferedImage image = reader.read(0, param);
            if (image == null || warned[0]) {
                throw unreadable();
            }
            return image;
        } catch (IOException | RuntimeException e) {
            if (e instanceof InvalidImageException invalid) {
                throw invalid;
            }
            throw unreadable();
        } finally {
            reader.dispose();
        }
    }

    /**
     * プログレッシブ形式の JPEG か（ヘッダーの SOF マーカーの種類で見る。画像の本体は読まない）。
     * 形式が読み取れないときは false（ふつうの形式として扱い、展開できなければ decode で断る）
     */
    static boolean isProgressiveJpeg(byte[] content) {
        int i = 2;
        while (i + 3 < content.length) {
            if ((content[i] & 0xFF) != 0xFF) {
                return false;
            }
            int marker = content[i + 1] & 0xFF;
            // マーカーの前の埋め草（0xFF の連続）は飛ばす
            if (marker == 0xFF) {
                i++;
                continue;
            }
            // SOF2・SOF6・SOF10・SOF14 がプログレッシブ。SOS（画像の本体の始まり）まで来たら、それより後に SOF はない
            if (marker == 0xC2 || marker == 0xC6 || marker == 0xCA || marker == 0xCE) {
                return true;
            }
            if (marker == 0xDA) {
                return false;
            }
            int length = ((content[i + 2] & 0xFF) << 8) | (content[i + 3] & 0xFF);
            i += 2 + length;
        }
        return false;
    }

    private InvalidImageException tooLarge() {
        return new InvalidImageException(
                "写真のファイルが大きすぎます（1枚あたり" + (maxFileSizeBytes / 1024 / 1024) + "MBまで）。" + SHRINK_HINT);
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
