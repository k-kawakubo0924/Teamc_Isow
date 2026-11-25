package com.teamc.isow.backend.image;

/**
 * 画像の保存先。開発中はサーバーのローカルフォルダ（LocalImageStorage）、
 * 公開時はクラウドストレージ（Cloudflare R2 など）の実装に差し替える。
 * 実装は設定 app.image.storage（環境変数 IMAGE_STORAGE）で選ぶ。
 *
 * <p>画像は、保存先の中での名前（キー。ImageUploadService が付けた推測できない名前）で指す。DB にはキーだけを保存し、
 * ブラウザに渡す URL は、応答を作るときにキーから組み立てる（ImageUrls）。
 *
 * <p>渡される画像は ImageUploadService で検証済みのものだけとし、実装側では検証しない。
 */
public interface ImageStorage {

    /**
     * 画像を保存し、キーを返す（DB にはこのキーのみを保存する）。
     *
     * @param key 保存するときの名前（ImageUploadService が生成した推測できない名前）
     * @param content 画像の中身
     * @param contentType 画像の MIME タイプ（実際の中身から判定したもの）
     */
    String store(String key, byte[] content, String contentType);

    /**
     * キーの画像の中身を読む（DM の画像の配信・通報の証拠として複製するため。docs/admin.md「通報」）。
     * 保存するときの名前の形でなければ IllegalArgumentException、読めなければ UncheckedIOException
     */
    byte[] read(String key);

    /**
     * キーの画像を削除する。存在しない場合は何もしない。
     * 投稿の登録が途中で失敗したときに、保存済みの画像を残さないために使う。
     */
    void delete(String key);
}
