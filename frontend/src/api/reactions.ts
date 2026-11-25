import { sendWithToken } from './client'

/** いいねの操作後の状態（バックエンドの LikeResponse） */
export type LikeResponse = {
  liked: boolean
  /** いいね件数（公開情報） */
  likeCount: number
}

/** お気に入りの操作後の状態（バックエンドの FavoriteResponse）。お気に入りは本人だけのもので、件数は返らない */
export type FavoriteResponse = {
  favorited: boolean
}

/**
 * いいねする（liked = true）／取り消す（liked = false）。
 * 何回呼んでも結果が同じになる API のため、すでにその状態でもエラーにはならない
 */
export function setLike(postId: number, liked: boolean, token: string): Promise<LikeResponse> {
  return sendWithToken<LikeResponse>(liked ? 'POST' : 'DELETE', `/api/posts/${postId}/like`, token)
}

/** お気に入りに追加する（favorited = true）／外す（favorited = false）。同じ状態でもエラーにはならない */
export function setFavorite(postId: number, favorited: boolean, token: string): Promise<FavoriteResponse> {
  return sendWithToken<FavoriteResponse>(favorited ? 'POST' : 'DELETE', `/api/posts/${postId}/favorite`, token)
}
