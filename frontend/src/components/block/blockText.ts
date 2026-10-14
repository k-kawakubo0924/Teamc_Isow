/** ブロックの確認に書く文（プロフィール・チャット画面で同じ文にする。docs/profile.md「ブロック」） */
export function blockConfirmText(username: string) {
  return {
    title: `@${username} さんをブロックしますか？`,
    body: 'お互いの投稿・プロフィールが見えなくなり、フォローは解除され、進行中の相談は終了します。相手には通知されません。',
  }
}

/** ブロックの解除の確認に書く文（プロフィール・ブロック中のアカウントで同じ文にする） */
export function unblockConfirmText(username: string) {
  return {
    title: `@${username} さんのブロックを解除しますか？`,
    body: 'お互いの投稿・プロフィールがまた見えるようになります。ブロックしたときに外れたフォロー・いいね・お気に入りは元に戻りません。',
  }
}
