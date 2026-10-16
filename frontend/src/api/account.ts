import { postJson } from './client'
import type { LoginResponse } from './auth'

/**
 * ログイン中にパスワードを変える（docs/settings.md「パスワード再設定（ログイン中の変更）」）。
 * ほかの端末はログアウトになり、この端末用の新しいトークンが返る（保存し直すこと）。
 * 失敗時は ApiError（400: 現在のパスワードが違う・規則に合わない・今と同じ）
 */
export function changePassword(currentPassword: string, newPassword: string, token: string): Promise<LoginResponse> {
  return postJson<LoginResponse>('/api/users/me/password', { currentPassword, newPassword }, token)
}

/**
 * メールアドレスの変更を申し込む（docs/settings.md「メールアドレスの変更」）。新しいアドレスに確認メールが届く。
 * 新しいアドレスがすでに使われていても同じ文言（message）が返る。
 * 失敗時は ApiError（400: 入力の誤り・現在のパスワードが違う / 429: 1時間に3回まで）
 */
export function requestEmailChange(
  currentPassword: string,
  newEmail: string,
  confirmEmail: string,
  token: string,
): Promise<{ message: string }> {
  return postJson<{ message: string }>('/api/users/me/email-change', { currentPassword, newEmail, confirmEmail }, token)
}

/** 確認のリンクの鍵で変更を完了する（ログインしていなくてよい）。鍵が使えなければ ApiError（400）。完了するとすべての端末がログアウトになる */
export function confirmEmailChange(linkToken: string): Promise<void> {
  return postJson<void>('/api/auth/email-change/confirm', { token: linkToken })
}
