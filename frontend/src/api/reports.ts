import { postJson } from './client'

/** 通報の対象の種類（バックエンドの ReportTargetType） */
export type ReportTargetType = 'POST' | 'USER' | 'MESSAGE'

/** 通報の理由（バックエンドの ReportReason の定数名） */
export type ReportReason =
  | 'SPAM'
  | 'HARASSMENT'
  | 'SEXUAL'
  | 'VIOLENCE'
  | 'PERSONAL_INFO'
  | 'IMPERSONATION'
  | 'UNAUTHORIZED_PHOTO'
  | 'OTHER'

/**
 * 通報の理由の選択肢（docs/admin.md「通報」）。バックエンドの ReportReason と同じ（変えるときは両方を直すこと）。
 * targets は、その理由を選べる対象の種類
 */
export const REPORT_REASONS: { reason: ReportReason; label: string; targets: ReportTargetType[] }[] = [
  { reason: 'SPAM', label: 'スパム・宣伝', targets: ['POST', 'USER', 'MESSAGE'] },
  { reason: 'HARASSMENT', label: '嫌がらせ・誹謗中傷', targets: ['POST', 'USER', 'MESSAGE'] },
  { reason: 'SEXUAL', label: '性的な内容', targets: ['POST', 'USER', 'MESSAGE'] },
  { reason: 'VIOLENCE', label: '暴力・危険な内容', targets: ['POST', 'USER', 'MESSAGE'] },
  { reason: 'PERSONAL_INFO', label: '個人情報の掲載', targets: ['POST', 'USER', 'MESSAGE'] },
  { reason: 'IMPERSONATION', label: 'なりすまし', targets: ['USER'] },
  { reason: 'UNAUTHORIZED_PHOTO', label: '他人の写真の無断転載', targets: ['POST'] },
  { reason: 'OTHER', label: 'その他', targets: ['POST', 'USER', 'MESSAGE'] },
]

/** 理由の表示名（管理画面でも使う） */
export function reportReasonLabel(reason: string): string {
  return REPORT_REASONS.find((option) => option.reason === reason)?.label ?? reason
}

/** 詳しい内容の文字数の上限（バックエンドの Report.MAX_COMMENT_LENGTH） */
export const MAX_REPORT_COMMENT_LENGTH = 500

export type ReportRequest = {
  targetType: ReportTargetType
  targetId: number
  reason: ReportReason
  /** 詳しい内容（任意。「その他」は必須） */
  comment: string
}

/** 通報の結果。ALREADY_REPORTED は、同じ対象をすでに通報していた */
export type ReportResult = { status: 'ACCEPTED' | 'ALREADY_REPORTED' }

/**
 * 通報する（POST /api/reports）。通報された人には何も伝わらない。
 * 失敗時は ApiError（400: 入力の誤り / 404: 対象がない / 429: 1日の上限）を投げる
 */
export function reportTarget(request: ReportRequest, token: string): Promise<ReportResult> {
  return postJson<ReportResult>('/api/reports', request, token)
}
