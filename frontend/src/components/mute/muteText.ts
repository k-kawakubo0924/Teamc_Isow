/*
 * ミュート（投稿の非表示。docs/profile.md「ミュート（投稿の非表示）」）の画面の文言。
 * - メニュー・ボタンは短く「非表示」「非表示を解除」（2026-11-26 に短くした）。「表示」だけだと何のことか分からないため、解除は「非表示を解除」
 * - 何をしたか・どういう状態かは、押した後の知らせ（muteNoticeText）・プロフィールの表示（MUTED_PROFILE_NOTE）・
 *   一覧の名前（「投稿を非表示にしている人」）が文で説明する。メニューを短くできるのは、これらが説明するため。こちらは短くしない
 * - 「ミュート」の語は画面に出さない（docs とコードだけで使う。DM の「通知オフ」と区別するため）
 */

/** メニュー・ボタンの文言。muted は今ミュートしているか（押すとその逆になる） */
export function muteMenuLabel(muted: boolean): string {
  return muted ? '非表示を解除' : '非表示'
}

/** ミュートしている相手のプロフィールに出す一文 */
export const MUTED_PROFILE_NOTE = 'この人の投稿を非表示にしています'

/**
 * 実行したことの知らせ。muted は実行した後の状態。
 * ミュートしても見ている投稿・プロフィールは消えないため、知らせないと押せたか分からない
 */
export function muteNoticeText(username: string, muted: boolean): string {
  return muted
    ? `@${username} さんの投稿を、ホームや検索に出さないようにしました`
    : `@${username} さんの投稿を、またホームや検索に出すようにしました`
}
