// 管理画面のお問い合わせの表示名（docs/admin.md「7. お問い合わせ」）

export const CONTACT_STATUS_LABELS: Record<string, string> = {
  OPEN: '未対応',
  RESOLVED: '対応済み',
  DISMISSED: '対応不要',
}

export const CONTACT_CHANNEL_LABELS: Record<string, string> = {
  NORMAL: '通常',
  SUSPENDED_APPEAL: '利用停止中の申し立て',
}
