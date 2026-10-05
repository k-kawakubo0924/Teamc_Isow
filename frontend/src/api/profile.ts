import { getJson, postForm, putJson } from './client'
import type { MasterOption } from './masters'

/**
 * バックエンドの ProfileResponse（GET /api/users/me・/api/users/{id}）。未設定の項目は null。
 * gender・ageGroup は GET /api/masters の code（表示する文言はそちらの label を使う）
 */
export type ProfileResponse = {
  id: number
  username: string
  displayName: string
  profileImageUrl: string | null
  gender: string | null
  heightCm: number | null
  ageGroup: string | null
  bodyType: MasterOption | null
  personalColor: MasterOption | null
  followingCount: number
  followerCount: number
  /** ログイン中のユーザー自身のプロフィールか */
  me: boolean
  /** ログイン中のユーザーがフォローしているか。自分のプロフィールでは null */
  followingByMe: boolean | null
}

/** プロフィール編集で送る内容（PUT /api/users/me）。名前以外は null で未設定に戻す */
export type ProfileUpdateRequest = {
  displayName: string
  gender: string | null
  heightCm: number | null
  ageGroup: string | null
  bodyTypeId: number | null
  personalColorId: number | null
}

export function fetchMyProfile(token: string): Promise<ProfileResponse> {
  return getJson<ProfileResponse>('/api/users/me', token)
}

/** プロフィール画像以外の項目をまとめて置き換える。応答は更新後のプロフィール */
export function updateMyProfile(request: ProfileUpdateRequest, token: string): Promise<ProfileResponse> {
  return putJson<ProfileResponse>('/api/users/me', request, token)
}

/** プロフィール画像を変更する（JPEG・PNG、10MB まで）。応答は更新後のプロフィール */
export function uploadProfileImage(image: File, token: string): Promise<ProfileResponse> {
  const form = new FormData()
  form.append('image', image)
  return postForm<ProfileResponse>('/api/users/me/profile-image', form, token)
}
