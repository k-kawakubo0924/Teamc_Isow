// 管理画面の通報の表示名（docs/admin.md「通報」）

export const REPORT_STATUS_LABELS: Record<string, string> = {
  OPEN: '未対応',
  RESOLVED: '対応済み',
  DISMISSED: '却下',
}

export const REPORT_TARGET_LABELS: Record<string, string> = {
  POST: '投稿',
  USER: 'ユーザー',
  MESSAGE: 'DM のメッセージ',
}

export const REPORT_CURRENT_STATE_LABELS: Record<string, string> = {
  EXISTS: '今もある',
  EDITED: '通報の後に編集された（下の内容は通報した時点のもの）',
  DELETED: '削除された（下の内容は通報した時点のコピー）',
}
