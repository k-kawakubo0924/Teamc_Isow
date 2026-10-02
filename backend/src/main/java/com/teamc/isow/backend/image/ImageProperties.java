package com.teamc.isow.backend.image;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 画像の保存に関する設定（application.properties の app.image.*）。
 *
 * @param storage 保存先の種類。local はサーバーのローカルフォルダ
 * @param maxFileSize 1ファイルあたりのサイズ上限
 * @param local ローカルフォルダに保存する場合の設定
 */
@ConfigurationProperties(prefix = "app.image")
public record ImageProperties(String storage, DataSize maxFileSize, Local local) {

    /**
     * @param dir 保存先フォルダ（相対パスの場合は起動したフォルダ＝backend/ からの位置）
     * @param publicBaseUrl ブラウザから画像を表示するときの URL の先頭部分（末尾の / なし）
     */
    public record Local(Path dir, String publicBaseUrl) {
    }
}
