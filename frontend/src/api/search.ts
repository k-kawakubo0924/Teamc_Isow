import { getJson, sendWithToken } from './client'
import { TIMELINE_PAGE_SIZE, type TimelineItem } from './posts'

/** 投稿の検索結果（バックエンドの PostSearchResponse。1ページ分） */
export type PostSearchResponse = {
  /** いいね数の多い順 → 新しい順 */
  posts: TimelineItem[]
  page: number
  size: number
  hasNext: boolean
  /** 条件に一致した投稿の件数 */
  totalCount: number
}

/**
 * 投稿を検索する（GET /api/search/posts）。指定しない条件は null・空文字。
 * キーワードを指定すると、サーバー側で検索履歴に記録される
 */
export function searchPosts(
  conditions: { q: string; categoryId: number | null; ageGroup: string | null },
  page: number,
  token: string,
  signal?: AbortSignal,
): Promise<PostSearchResponse> {
  const params = new URLSearchParams({ page: String(page), size: String(TIMELINE_PAGE_SIZE) })
  if (conditions.q.trim() !== '') params.set('q', conditions.q.trim())
  if (conditions.categoryId !== null) params.set('categoryId', String(conditions.categoryId))
  if (conditions.ageGroup !== null) params.set('ageGroup', conditions.ageGroup)
  return getJson<PostSearchResponse>(`/api/search/posts?${params}`, token, signal)
}

/** ユーザーの検索結果の1件（バックエンドの UserSearchResponse.Item）。未設定の項目は null */
export type UserSearchItem = {
  id: number
  username: string
  displayName: string
  profileImageUrl: string | null
  heightCm: number | null
  /** GET /api/masters の genders の code */
  gender: string | null
  followingByMe: boolean
}

/** ユーザーの検索結果（バックエンドの UserSearchResponse。1ページ分） */
export type UserSearchResponse = {
  /** 完全一致 → 前方一致 → 部分一致 → ユーザー名の順。自分は含まない */
  users: UserSearchItem[]
  page: number
  size: number
  hasNext: boolean
  totalCount: number
}

/**
 * ユーザー名・表示名でユーザーを検索する（GET /api/search/users）。q が空なら自分以外の全ユーザー。
 * キーワードを指定すると、サーバー側で検索履歴に記録される
 */
export function searchUsers(q: string, page: number, token: string, signal?: AbortSignal): Promise<UserSearchResponse> {
  const params = new URLSearchParams({ page: String(page), size: String(TIMELINE_PAGE_SIZE) })
  if (q.trim() !== '') params.set('q', q.trim())
  return getJson<UserSearchResponse>(`/api/search/users?${params}`, token, signal)
}

/** 検索履歴の1件（バックエンドの SearchHistoryListResponse.Item） */
export type SearchHistoryItem = {
  id: number
  /** 検索したキーワード（検索欄に入れ直す値） */
  keyword: string
  /** 最後に検索した日時 */
  searchedAt: string
}

/** 自分の検索履歴を新しい順に取得する（GET /api/search/history。最大20件） */
export async function fetchSearchHistory(token: string): Promise<SearchHistoryItem[]> {
  const res = await getJson<{ histories: SearchHistoryItem[] }>('/api/search/history', token)
  return res.histories
}

/** 自分の検索履歴を1件削除する。すでに削除済みでもエラーにはならない */
export function deleteSearchHistory(id: number, token: string): Promise<void> {
  return sendWithToken<void>('DELETE', `/api/search/history/${id}`, token)
}

/** 自分の検索履歴をすべて削除する */
export function deleteAllSearchHistory(token: string): Promise<void> {
  return sendWithToken<void>('DELETE', '/api/search/history', token)
}

/** 候補ワードの1件。source は POPULAR（よく使われているタグ）か OFFICIAL（足りない分を補った公式タグ） */
export type SuggestionWord = {
  name: string
  source: 'POPULAR' | 'OFFICIAL'
}

/**
 * 検索項目が選ばれたときに出す候補ワード（GET /api/search/suggestions。最大10件）。
 * categoryId を省略すると、全投稿でよく使われているタグを返す
 */
export async function fetchSuggestions(
  categoryId: number | null,
  token: string,
  signal?: AbortSignal,
): Promise<SuggestionWord[]> {
  const params = categoryId === null ? '' : `?categoryId=${categoryId}`
  const res = await getJson<{ words: SuggestionWord[] }>(`/api/search/suggestions${params}`, token, signal)
  return res.words
}
