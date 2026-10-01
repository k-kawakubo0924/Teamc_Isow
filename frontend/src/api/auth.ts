import { postJson } from './client'

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
