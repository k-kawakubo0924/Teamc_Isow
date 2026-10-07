import { getJson, sendWithToken } from './client'

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
