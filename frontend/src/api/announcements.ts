import { getJson, sendWithToken } from './client'
import { TIMELINE_PAGE_SIZE } from './posts'

/**
 * お知らせの1件（バックエンドの AnnouncementListResponse.Item）。題名・本文は文字だけ（HTML として扱わない）。
 * 一覧を押したらその場で本文を開くため、本文も一覧に含まれる
 */
export type Announcement = {
  id: number
  title: string
  /** 改行は \n */
  body: string
  /** 発行日時（タイムゾーンなしの日時の文字列） */
  publishedAt: string
  /** 開いたもの、または登録より前に発行されたものは既読 */
  read: boolean
}

/** お知らせの一覧（1ページ分。バックエンドの AnnouncementListResponse） */
export type AnnouncementListResponse = {
  /** 発行日時の新しい順。取り消したものは含まない */
  announcements: Announcement[]
  page: number
  size: number
  hasNext: boolean
}

/** お知らせの一覧（GET /api/announcements） */
export function fetchAnnouncements(page: number, token: string, signal?: AbortSignal): Promise<AnnouncementListResponse> {
  return getJson<AnnouncementListResponse>(`/api/announcements?page=${page}&size=${TIMELINE_PAGE_SIZE}`, token, signal)
}

/** 既読にする（すでに既読なら何も変わらない） */
export function markAnnouncementRead(id: number, token: string): Promise<void> {
  return sendWithToken<void>('POST', `/api/announcements/${id}/read`, token)
}
