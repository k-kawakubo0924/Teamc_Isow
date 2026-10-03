import { getJson, postForm } from './client'

/** 投稿作成で送る内容。images は表示順（1枚目がサムネイル） */
export type CreatePostRequest = {
  images: File[]
  title: string
  fashionCategoryId: number
  /** タグ名（公式タグ・手入力のタグとも名前で送る） */
  tags: string[]
  /** 未入力なら送らない */
  wornItems: string
  description: string
  /** 未入力なら送らない */
  referenceUrl: string
}

/** バックエンドの PostResponse */
export type PostResponse = {
  id: number
  title: string
  fashionCategory: { id: number; name: string }
  tags: { id: number; name: string; official: boolean }[]
  wornItems: string | null
  description: string
  referenceUrl: string | null
  /** 表示順。1枚目がサムネイル */
  imageUrls: string[]
  author: { id: number; username: string }
  /** いいね件数（公開情報） */
  likeCount: number
  /** ログイン中のユーザーがいいねしているか */
  likedByMe: boolean
  /** ログイン中のユーザーがお気に入りにしているか（本人にだけ返る） */
  favoritedByMe: boolean
  createdAt: string
}

/** ホームの投稿一覧のタブ（フォロー中はフォロー機能の実装時に追加する） */
export type TimelineTab = 'recommended' | 'latest'

/** ホームの投稿一覧の1件（バックエンドの TimelineResponse.Item） */
export type TimelineItem = {
  id: number
  /** 1枚目の写真 */
  thumbnailUrl: string
  fashionCategory: { id: number; name: string }
  /** heightCm は身長（cm）。未設定は null で、表示を省く */
  author: { id: number; username: string; heightCm: number | null }
  likeCount: number
  likedByMe: boolean
  favoritedByMe: boolean
  createdAt: string
}

/** バックエンドの TimelineResponse（1ページ分） */
export type TimelineResponse = {
  posts: TimelineItem[]
  tab: TimelineTab
  page: number
  size: number
  hasNext: boolean
}

/**
 * ホームの投稿一覧（GET /api/posts）。page は 0 から。
 * ページ番号で区切るため、読み込みの途中で投稿やいいねが増えると、同じ投稿が次のページにも出ることがある。
 * 呼び出し側で id が重複したものを省くこと
 */
export function fetchTimeline(
  tab: TimelineTab,
  page: number,
  token: string,
  signal?: AbortSignal,
): Promise<TimelineResponse> {
  return getJson<TimelineResponse>(`/api/posts?tab=${tab}&page=${page}&size=${TIMELINE_PAGE_SIZE}`, token, signal)
}

/** ホームの一覧で1回に読み込む件数（docs/home.md） */
export const TIMELINE_PAGE_SIZE = 20

/**
 * 投稿を作成する（POST /api/posts、multipart/form-data）。
 * 失敗時は ApiError（400: 入力エラー / 413: ファイルが大きすぎる）を投げる
 */
export function createPost(request: CreatePostRequest, token: string): Promise<PostResponse> {
  const form = new FormData()
  // 同じ名前で追加した順が、そのまま写真の表示順になる
  for (const image of request.images) {
    form.append('images', image)
  }
  form.append('title', request.title)
  form.append('fashionCategoryId', String(request.fashionCategoryId))
  for (const tag of request.tags) {
    form.append('tags', tag)
  }
  if (request.wornItems !== '') form.append('wornItems', request.wornItems)
  form.append('description', request.description)
  if (request.referenceUrl !== '') form.append('referenceUrl', request.referenceUrl)
  return postForm<PostResponse>('/api/posts', form, token)
}
