import { getJson, postJson, sendWithToken } from './client'

/*
 * 管理者向けの API（docs/admin.md）。/api/admin/** はサーバー側で管理者だけに許可している。
 * 管理者でない人には、存在しない URL と同じ 404 が返る（画面側の判定は表示を出し分けるためだけで、守りはサーバー側で行う）
 */

/** ログイン中の管理者（バックエンドの AdminController.AdminMeResponse） */
export type AdminMe = {
  id: number
  username: string
}

/** ログイン中のユーザーが管理者か確かめる。管理者なら情報を返し、そうでなければ ApiError（404）になる */
export function getAdminMe(token: string): Promise<AdminMe> {
  return getJson<AdminMe>('/api/admin/me', token)
}

/** マスタ管理で扱う種類（docs/admin.md「マスタの管理」）。公式タグだけ API の形が違う */
export type AdminMasterKind = 'fashion-categories' | 'body-types' | 'personal-colors' | 'official-tags'

/** マスタ・公式タグの1件（バックエンドの AdminMasterItem）。無効なものも返す */
export type AdminMasterItem = {
  id: number
  name: string
  displayOrder: number
  active: boolean
}

/** 追加の結果。madeOfficial は公式タグだけ：true なら、同じ名前の手入力のタグを公式にした */
export type AdminMasterCreateResult = {
  item: AdminMasterItem
  madeOfficial: boolean
}

function masterPath(kind: AdminMasterKind): string {
  return kind === 'official-tags' ? '/api/admin/official-tags' : `/api/admin/masters/${kind}`
}

/** 一覧（無効なものも含めて、並び順・ID 順） */
export async function listMasters(kind: AdminMasterKind, token: string): Promise<AdminMasterItem[]> {
  const res = await getJson<{ items: AdminMasterItem[] }>(masterPath(kind), token)
  return res.items
}

/** 追加する。重複・空・長すぎる名前は ApiError（400。errors.name に理由） */
export async function createMaster(kind: AdminMasterKind, name: string, token: string): Promise<AdminMasterCreateResult> {
  if (kind === 'official-tags') {
    const res = await postJson<{ tag: AdminMasterItem; madeOfficial: boolean }>(masterPath(kind), { name }, token)
    return { item: res.tag, madeOfficial: res.madeOfficial }
  }
  const item = await postJson<AdminMasterItem>(masterPath(kind), { name }, token)
  return { item, madeOfficial: false }
}

/** 無効にする（active = false）か、有効に戻す（active = true）。操作後の1件を返す */
export function setMasterActive(kind: AdminMasterKind, id: number, active: boolean, token: string): Promise<AdminMasterItem> {
  return sendWithToken<AdminMasterItem>('POST', `${masterPath(kind)}/${id}/${active ? 'activate' : 'deactivate'}`, token)
}

/** 管理画面の一覧の1ページの件数（ホームの一覧・通知一覧と同じ。サーバー側の上限は 50） */
export const ADMIN_PAGE_SIZE = 20

/** 発行したお知らせ（バックエンドの AdminAnnouncementResponse）。題名・本文は文字だけ（HTML として扱わない） */
export type AdminAnnouncement = {
  id: number
  title: string
  /** 改行は \n */
  body: string
  /** 発行日時（タイムゾーンなしの日時の文字列） */
  publishedAt: string
  publisherId: number
  /** 発行した管理者のユーザー名（発行した時点の値） */
  publisherUsername: string
}

export type AdminAnnouncementList = {
  items: AdminAnnouncement[]
  page: number
  size: number
  hasNext: boolean
}

/** 発行済みのお知らせ（新しい順） */
export function listAnnouncements(page: number, token: string): Promise<AdminAnnouncementList> {
  return getJson<AdminAnnouncementList>(`/api/admin/announcements?page=${page}&size=${ADMIN_PAGE_SIZE}`, token)
}

/** 発行する。入力の誤りは ApiError（400。errors.title・errors.body に理由） */
export function publishAnnouncement(title: string, body: string, token: string): Promise<AdminAnnouncement> {
  return postJson<AdminAnnouncement>('/api/admin/announcements', { title, body }, token)
}

/** 管理操作のログの1件（バックエンドの AdminOperationLogListResponse.Item） */
export type AdminOperationLog = {
  id: number
  operatedAt: string
  /** システムの操作なら null */
  operatorId: number | null
  /** 操作した時点のユーザー名。システムの操作なら「システム」 */
  operatorUsername: string
  /** システムの操作（起動時の管理者への昇格など、人が操作していないもの）か */
  system: boolean
  /** 操作の種類（AdminAction の定数名） */
  action: string
  /** 対象の種類（AdminTargetType の定数名） */
  targetType: string
  targetId: number
  detail: string | null
}

export type AdminOperationLogList = {
  logs: AdminOperationLog[]
  page: number
  size: number
  hasNext: boolean
}

/** 管理操作のログ（新しい順） */
export function listOperationLogs(page: number, token: string): Promise<AdminOperationLogList> {
  return getJson<AdminOperationLogList>(`/api/admin/operation-logs?page=${page}&size=${ADMIN_PAGE_SIZE}`, token)
}
