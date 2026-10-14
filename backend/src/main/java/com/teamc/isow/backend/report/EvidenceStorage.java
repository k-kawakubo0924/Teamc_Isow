package com.teamc.isow.backend.report;

/**
 * 通報の証拠の写真の保存先（docs/admin.md「通報」の「証拠の写真の配信」）。
 *
 * <ul>
 *   <li>公開の画像（/uploads/**）とは別の場所に置き、ブラウザから直接は取得できないようにする。
 *       配信は管理者向けの API だけが、管理者であることを確かめてから read() で中身を読んで返す</li>
 *   <li>扱うのは、すでに ImageUploadService.prepare() を通してこのアプリに保存されているファイルの複製だけ
 *       （prepare() を再度通さない理由は CLAUDE.md）。外から受け取った画像をここに保存しないこと</li>
 *   <li>ファイルは名前で指定する。名前は storeCopy() が付けた推測できない乱数の名前だけを受け付け、フォルダの外は指せない</li>
 * </ul>
 * 開発中はローカルフォルダ（LocalEvidenceStorage）。公開時は、公開しない（非公開の）クラウドストレージの実装に差し替える
 */
public interface EvidenceStorage {

    /**
     * 複製した中身を保存し、付けたファイル名を返す（DB にはこの名前だけを保存する）
     *
     * @param extension 拡張子（jpg・png）
     */
    String storeCopy(byte[] content, String extension);

    /** storeCopy() が付けた名前のファイルの中身 */
    byte[] read(String fileName);

    /** storeCopy() が付けた名前のファイルを1つ消す。存在しなければ何もしない。消せなければ例外 */
    void delete(String fileName);
}
