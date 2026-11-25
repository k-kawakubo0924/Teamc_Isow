import { getJson, postJson } from './client'

export type RegisterRequest = {
  email: string
  phoneNumber: string
  password: string
  username: string
}

/** バックエンドの UserResponse。パスワードは含まれない */
export type UserResponse = {
  id: number
  email: string
  phoneNumber: string
  username: string
}

/** 新規会員登録。失敗時は ApiError（409: 重複 / 400: 形式エラー）を投げる */
export function register(request: RegisterRequest): Promise<UserResponse> {
  return postJson<UserResponse>('/api/auth/register', request)
}

export type LoginRequest = {
  email: string
  password: string
}

/** バックエンドの LoginResponse。expiresAt は ISO 8601 形式の日時 */
export type LoginResponse = {
  token: string
  expiresAt: string
}

/** ログイン。失敗時は ApiError（401: メールアドレスまたはパスワードが違う / 400: 未入力）を投げる */
export function login(request: LoginRequest): Promise<LoginResponse> {
  return postJson<LoginResponse>('/api/auth/login', request)
}

/**
 * パスワードの再設定のメールを頼む（ログイン前。docs/auth.md「パスワードの再設定」）。
 * 登録の有無にかかわらず、同じ文言（message）が返る。形式の誤りだけは ApiError（400）
 */
export function requestPasswordReset(email: string): Promise<{ message: string }> {
  return postJson<{ message: string }>('/api/auth/password-reset/request', { email })
}

/** メールのリンクの鍵で、新しいパスワードを設定する。鍵が使えなければ ApiError（400）。成功するとすべての端末がログアウトになる */
export function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  return postJson<void>('/api/auth/password-reset/confirm', { token, newPassword })
}

/** ログイン中のユーザー情報。トークンが無効なら ApiError（401）を投げる */
export function me(token: string): Promise<UserResponse> {
  return getJson<UserResponse>('/api/auth/me', token)
}
