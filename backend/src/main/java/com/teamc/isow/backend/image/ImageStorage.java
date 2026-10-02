package com.teamc.isow.backend.image;

/**
 * 画像の保存先。開発中はサーバーのローカルフォルダ（LocalImageStorage）、
 * 公開時はクラウドストレージ（Cloudflare R2 など）の実装に差し替える。
 * 実装は設定 app.image.storage（環境変数 IMAGE_STORAGE）で選ぶ。
 *
 * <p>渡される画像は ImageUploadService で検証済みのものだけとし、実装側では検証しない。
 */
public interface ImageStorage {

    /**
     * 画像を保存し、ブラウザから表示できる URL を返す（DB にはこの URL のみを保存する）。
     *
     * @param fileName 保存するファイル名（ImageUploadService が生成した推測できない名前）
     * @param content 画像の中身
     * @param contentType 画像の MIME タイプ（実際の中身から判定したもの）
     */
    String store(String fileName, byte[] content, String contentType);
}
