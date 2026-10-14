import { getJson, sendWithToken } from './client'

/** ブロックした相手1人（バックエンドの BlockListResponse.Item） */
export type BlockedUser = {
  userId: number
  username: string
  displayName: string
  profileImageUrl: string | null
  /** ブロックした日時（タイムゾーンなしの日時の文字列） */
  blockedAt: string
}

/** ブロック中の一覧（1ページ分。ブロックした新しい順） */
export type BlockListResponse = {
  items: BlockedUser[]
  page: number
  size: number
  hasNext: boolean
}

/** ブロック中の一覧で1回に読み込む件数 */
export const BLOCK_PAGE_SIZE = 20

/**
 * ブロックする（POST /api/users/{id}/block。docs/profile.md「ブロック」）。すでにブロックしていても成功。
 * お互いの投稿・プロフィールが見えなくなり、フォロー・いいね・お気に入り・通知は消え、進行中の相談は終了する。相手には通知されない
 */
export function blockUser(userId: number, token: string): Promise<void> {
  return sendWithToken<void>('POST', `/api/users/${userId}/block`, token)
}

/** ブロックを解除する（DELETE /api/users/{id}/block）。切ったつながり（フォローなど）は戻らない */
export function unblockUser(userId: number, token: string): Promise<void> {
  return sendWithToken<void>('DELETE', `/api/users/${userId}/block`, token)
}

/** 自分がブロックしている人の一覧（GET /api/blocks） */
export function fetchBlocks(page: number, token: string, signal?: AbortSignal): Promise<BlockListResponse> {
  return getJson<BlockListResponse>(`/api/blocks?page=${page}&size=${BLOCK_PAGE_SIZE}`, token, signal)
}
