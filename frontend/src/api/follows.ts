import { sendWithToken } from './client'

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
