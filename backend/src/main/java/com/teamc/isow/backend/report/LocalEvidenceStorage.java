package com.teamc.isow.backend.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 開発用：証拠の写真をサーバーのローカルフォルダに保存する（EvidenceStorage）。
 *
 * <p>保存先は公開の画像のフォルダ（app.image.local.dir。/uploads/** で配信される）の外にあること。
 * 中にある（または公開のフォルダがこの中にある）設定では起動しない（証拠が /uploads/** から取得できてしまうため）。
 * このフォルダを配信する設定は作らないこと
 */
public class LocalEvidenceStorage implements EvidenceStorage {

    /** storeCopy() が付ける名前の形（乱数 + 拡張子）。これ以外の名前は読み書き・削除しない */
    private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{32}\\.(jpg|png)");

    private final Path baseDir;

    /**
     * @param evidenceDir 証拠の保存先
     * @param publicImageDir 公開の画像の保存先（/uploads/** で配信される。重ならないことを確かめるために受け取る）
     */
    public LocalEvidenceStorage(Path evidenceDir, Path publicImageDir) {
        this.baseDir = evidenceDir.toAbsolutePath().normalize();
        Path publicDir = publicImageDir.toAbsolutePath().normalize();
        if (baseDir.startsWith(publicDir) || publicDir.startsWith(baseDir)) {
            throw new IllegalStateException("証拠の保存先（app.evidence.local.dir）は、公開の画像の保存先（app.image.local.dir）"
                    + "の外に置いてください。重なっていると、証拠の写真が /uploads/** から取得できてしまいます: "
                    + baseDir + " / " + publicDir);
        }
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("証拠の保存先のフォルダを作成できません: " + baseDir, e);
        }
    }

    @Override
    public String storeCopy(byte[] content, String extension) {
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        try {
            Files.write(resolve(fileName), content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("証拠の写真を保存できませんでした", e);
        }
        return fileName;
    }

    @Override
    public byte[] read(String fileName) {
        try {
            return Files.readAllBytes(resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException("証拠の写真を読めませんでした: " + fileName, e);
        }
    }

    @Override
    public void delete(String fileName) {
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (IOException e) {
            throw new UncheckedIOException("証拠の写真を削除できませんでした: " + fileName, e);
        }
    }

    /** storeCopy() が付けた形の名前で、保存先のフォルダ直下のファイルであることを確かめる */
    private Path resolve(String fileName) {
        if (fileName == null || !FILE_NAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("証拠のファイル名ではありません: " + fileName);
        }
        Path target = baseDir.resolve(fileName).normalize();
        if (!baseDir.equals(target.getParent())) {
            throw new IllegalArgumentException("不正なファイル名です: " + fileName);
        }
        return target;
    }
}
