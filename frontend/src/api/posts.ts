import { postForm } from './client'

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
  createdAt: string
}

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
