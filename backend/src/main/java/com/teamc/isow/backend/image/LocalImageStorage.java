package com.teamc.isow.backend.image;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 開発用：画像をサーバーのローカルフォルダに、キーをファイル名にして保存する。
 * 保存した画像は LocalImageWebConfig の設定で /uploads/** から配信する（URL は ImageUrls が組み立てる）。
 * app.image.storage が local（未設定を含む）のときだけ有効になる。
 */
@Component
@ConditionalOnProperty(name = "app.image.storage", havingValue = "local", matchIfMissing = true)
public class LocalImageStorage implements ImageStorage {

    private final Path baseDir;

    public LocalImageStorage(ImageProperties properties) {
        this.baseDir = properties.local().dir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("画像の保存先フォルダを作成できません: " + baseDir, e);
        }
    }

    @Override
    public String store(String key, byte[] content, String contentType) {
        Path target = resolve(key);
        try {
            // 同じ名前のファイルがあっても上書きしない（名前は乱数なので通常は起こらない）
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new UncheckedIOException("画像を保存できませんでした", e);
        }
        return key;
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("画像を読めませんでした: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("画像を削除できませんでした: " + key, e);
        }
    }

    /** キーの形であり、保存先フォルダ直下のファイルを指していることを確認する（../ などでフォルダの外を指させない） */
    private Path resolve(String key) {
        Path target = baseDir.resolve(ImageKeys.require(key)).normalize();
        if (!baseDir.equals(target.getParent())) {
            throw new IllegalArgumentException("不正なファイル名です: " + key);
        }
        return target;
    }

    /** 保存先フォルダ（配信の設定で使う） */
    Path getBaseDir() {
        return baseDir;
    }
}
