import { getJson } from './client'

/** バックエンドの TagCandidateResponse */
export type TagCandidate = {
  id: number
  name: string
  official: boolean
}

/**
 * 入力中の文字に部分一致するタグの候補（GET /api/tags?q=...、最大20件）。
 * 大文字小文字・全角半角の違いはバックエンドで吸収される
 */
export function searchTags(query: string, token: string, signal?: AbortSignal): Promise<TagCandidate[]> {
  return getJson<TagCandidate[]>(`/api/tags?q=${encodeURIComponent(query)}`, token, signal)
}
