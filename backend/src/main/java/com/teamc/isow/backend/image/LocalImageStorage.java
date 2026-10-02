package com.teamc.isow.backend.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 開発用：画像をサーバーのローカルフォルダに保存する。
 * 保存した画像は LocalImageWebConfig の設定で /uploads/** から配信する。
 * app.image.storage が local（未設定を含む）のときだけ有効になる。
 */
@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

    private final Path baseDir;
    private final String publicBaseUrl;

    public LocalImageStorage(ImageProperties properties) {
        this.baseDir = properties.local().dir().toAbsolutePath().normalize();
        this.publicBaseUrl = properties.local().publicBaseUrl().replaceAll("/+$", "");
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("画像の保存先フォルダを作成できません: " + baseDir, e);
        }
    }

    @Override
    public String store(String fileName, byte[] content, String contentType) {
        Path target = resolve(fileName);
        try {
            // 同じ名前のファイルがあっても上書きしない（名前は乱数なので通常は起こらない）
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("画像を保存できませんでした", e);
        }
        return publicBaseUrl + "/" + fileName;
    }

    @Override
    public void delete(String url) {
        String prefix = publicBaseUrl + "/";
        if (url == null || !url.startsWith(prefix)) {
            throw new IllegalArgumentException("この保存先の URL ではありません: " + url);
        }
        try {
            Files.deleteIfExists(resolve(url.substring(prefix.length())));
        } catch (IOException e) {
            throw new UncheckedIOException("画像を削除できませんでした: " + url, e);
        }
    }

    /** 保存先フォルダ直下のファイルを指していることを確認する（../ などでフォルダの外を指させない） */
    private Path resolve(String fileName) {
        Path target = baseDir.resolve(fileName).normalize();
        if (!baseDir.equals(target.getParent())) {
            throw new IllegalArgumentException("不正なファイル名です: " + fileName);
        }
        return target;
    }

    /** 保存先フォルダ（配信の設定で使う） */
    Path getBaseDir() {
        return baseDir;
    }
}
