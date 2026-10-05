import { getJson, postForm, putJson } from './client'
import type { MasterOption } from './masters'
import { TIMELINE_PAGE_SIZE, type TimelineItem } from './posts'

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
  /** 投稿の件数 */
  postCount: number
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

/** 指定したユーザーのプロフィール。存在しないユーザーは ApiError（404） */
export function fetchProfile(userId: number, token: string): Promise<ProfileResponse> {
  return getJson<ProfileResponse>(`/api/users/${userId}`, token)
}

/** プロフィールの投稿一覧・お気に入り一覧（バックエンドの PostCardListResponse）。投稿はホームの一覧と同じ形 */
export type PostCardListResponse = {
  posts: TimelineItem[]
  page: number
  size: number
  hasNext: boolean
}

/** 指定したユーザーの投稿（新しい順） */
export function fetchUserPosts(userId: number, page: number, token: string, signal?: AbortSignal) {
  return getJson<PostCardListResponse>(`/api/users/${userId}/posts?page=${page}&size=${TIMELINE_PAGE_SIZE}`, token, signal)
}

/** 自分のお気に入り（お気に入りにした新しい順） */
export function fetchMyFavorites(page: number, token: string, signal?: AbortSignal) {
  return getJson<PostCardListResponse>(`/api/users/me/favorites?page=${page}&size=${TIMELINE_PAGE_SIZE}`, token, signal)
}

/** fetchFollowingPosts の seed の範囲（1 以上、この値未満） */
const SHUFFLE_MODULUS = 2147483647

/** fetchFollowingPosts に渡す seed を作る。一覧を開くたびに1回だけ作り、次のページでも同じ値を使う */
export function newShuffleSeed(): number {
  return 1 + Math.floor(Math.random() * (SHUFFLE_MODULUS - 1))
}

/** 指定したユーザーがフォローしている人の投稿（seed で決まるランダムな順） */
export function fetchFollowingPosts(userId: number, seed: number, page: number, token: string, signal?: AbortSignal) {
  return getJson<PostCardListResponse>(
    `/api/users/${userId}/following-posts?seed=${seed}&page=${page}&size=${TIMELINE_PAGE_SIZE}`,
    token,
    signal,
  )
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
