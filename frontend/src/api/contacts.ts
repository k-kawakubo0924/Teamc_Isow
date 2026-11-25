import { postJson } from './client'

/** お問い合わせの種類（バックエンドの ContactCategory の定数名） */
export type ContactCategory = 'APP' | 'USER' | 'ACCOUNT' | 'OTHER'

/**
 * お問い合わせの種類の選択肢（docs/settings.md「お問い合わせ」、design/Contact us.png）。
 * バックエンドの ContactCategory と同じ（変えるときは両方を直すこと）
 */
export const CONTACT_CATEGORIES: { category: ContactCategory; label: string }[] = [
  { category: 'APP', label: 'アプリについて' },
  { category: 'USER', label: 'ユーザーについて' },
  { category: 'ACCOUNT', label: 'アカウントについて' },
  { category: 'OTHER', label: 'その他' },
]

/** 種類の表示名（管理画面でも使う） */
export function contactCategoryLabel(category: string): string {
  return CONTACT_CATEGORIES.find((option) => option.category === category)?.label ?? category
}

/** 内容の文字数の上限（バックエンドの ContactInquiry.MAX_BODY_LENGTH） */
export const MAX_CONTACT_BODY_LENGTH = 1000

export type ContactRequest = {
  /** 返信先メールアドレス（任意。空なら送らない扱い。登録済みと同じならサーバーは保存しない） */
  email: string
  category: ContactCategory
  body: string
}

export type ContactResult = { status: 'ACCEPTED' }

/**
 * ログイン中のお問い合わせ（POST /api/contacts）。
 * 失敗時は ApiError（400: 入力の誤り / 429: 1日の上限）を投げる
 */
export function sendContact(request: ContactRequest, token: string): Promise<ContactResult> {
  return postJson<ContactResult>('/api/contacts', request, token)
}

/**
 * 利用停止中の申し立て（POST /api/contacts/appeal）。トークンは付けず、利用停止の 403 で受け取った申し立て用の鍵を送る。
 * 失敗時は ApiError（400: 入力の誤り / 401: 鍵の期限切れ / 409: 利用停止の状態が変わった / 429: 1日の上限）を投げる
 */
export function sendAppeal(request: ContactRequest & { appealToken: string }): Promise<ContactResult> {
  return postJson<ContactResult>('/api/contacts/appeal', request)
}
