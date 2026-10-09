/**
 * サーバーの日時（タイムゾーンなしの "2026-10-09T15:30:00.123"）を「2026/10/09 15:30」の形にする。
 * サーバーの時刻をそのまま出す（ブラウザのタイムゾーンで変換しない）。withSeconds なら秒まで出す
 */
export function formatAdminDateTime(value: string, withSeconds = false): string {
  const m = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/)
  if (m === null) return value
  const [, year, month, day, hour, minute, second = '00'] = m
  return `${year}/${month}/${day} ${hour}:${minute}${withSeconds ? `:${second}` : ''}`
}
