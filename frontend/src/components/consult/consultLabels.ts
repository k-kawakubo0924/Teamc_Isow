import type { ConsultationStatus } from '../../api/consultations'

/**
 * ボタンの下などに出す、申し込めない理由。出さなくてよい状態（申し込める・申請済み・相談中など、ボタンで分かるもの）は null。
 * 上限に達している場合は「現在、新しい相談を受け付けていません。」（docs/dm.md）
 */
export function consultNote(status: ConsultationStatus | null): string | null {
  if (status === null) return null
  return status.reason === 'LIMIT_REACHED' || status.reason === 'REJECTED_RECENTLY' ? status.message : null
}
