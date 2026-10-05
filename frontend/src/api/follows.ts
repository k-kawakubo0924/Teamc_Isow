import { getJson, sendWithToken } from './client'
import { TIMELINE_PAGE_SIZE } from './posts'

/** フォロー中一覧・フォロワー一覧の並び順。newest はフォローの新しい順、oldest は古い順 */
export type FollowSort = 'newest' | 'oldest'

/** 一覧の1件（バックエンドの FollowListResponse.Item）。未設定の項目は null */
export type FollowListItem = {
  id: number
  username: string
  displayName: string
  profileImageUrl: string | null
  heightCm: number | null
  /** GET /api/masters の genders の code */
  gender: string | null
  /** フォローした日時 */
  followedAt: string
  /** ログイン中のユーザーがこの人をフォローしているか。自分のフォロー中一覧で解除から5分以内のものは false */
  followingByMe: boolean
}

/** バックエンドの FollowListResponse（1ページ分） */
export type FollowListResponse = {
  users: FollowListItem[]
  /** フォロー数・フォロワー数（有効なフォローのみ。検索で絞り込む前の件数） */
  totalCount: number
  sort: FollowSort
  page: number
  size: number
  hasNext: boolean
}

/**
 * フォロー中一覧（kind = followings）・フォロワー一覧（kind = followers）。
 * query はユーザー名・表示名の一部で、空なら絞り込まない
 */
export function fetchFollowList(
  kind: 'followings' | 'followers',
  userId: number,
  query: string,
  sort: FollowSort,
  page: number,
  token: string,
  signal?: AbortSignal,
): Promise<FollowListResponse> {
  const params = new URLSearchParams({ sort, page: String(page), size: String(TIMELINE_PAGE_SIZE) })
  if (query.trim() !== '') params.set('q', query.trim())
  return getJson<FollowListResponse>(`/api/users/${userId}/${kind}?${params}`, token, signal)
}

/** フォロー・解除の操作後の状態（バックエンドの FollowResponse） */
export type FollowResponse = {
  following: boolean
  /** 相手のフォロワー数 */
  followerCount: number
}

/**
 * フォローする（following = true）／解除する（following = false）。
 * 何回呼んでも結果が同じになる API のため、すでにその状態でもエラーにはならない
 */
export function setFollow(userId: number, following: boolean, token: string): Promise<FollowResponse> {
  return sendWithToken<FollowResponse>(following ? 'POST' : 'DELETE', `/api/users/${userId}/follow`, token)
}
