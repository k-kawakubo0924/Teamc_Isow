const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/**
 * 日時が今からどのくらい前かを、短い言葉で返す（例：たった今、5分前、2時間前、3日前、2週間前、1か月前、1年前）。
 * docs/profile.md「フォローしたタイミングが簡単に分かる」・docs/notification.md の表示に合わせる。
 *
 * @param dateTime バックエンドの日時（タイムゾーンなしの ISO 形式。サーバーと同じ日本時間として扱う）
 */
export function timeAgo(dateTime: string, now: Date = new Date()): string {
  const diff = Math.max(0, now.getTime() - new Date(dateTime).getTime())
  if (diff < MINUTE) return 'たった今'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}分前`
  if (diff < DAY) return `${Math.floor(diff / HOUR)}時間前`
  const days = Math.floor(diff / DAY)
  if (days < 7) return `${days}日前`
  if (days < 30) return `${Math.floor(days / 7)}週間前`
  if (days < 365) return `${Math.floor(days / 30)}か月前`
  return `${Math.floor(days / 365)}年前`
}
