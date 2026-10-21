import { getBlob, getJson, postJson, sendWithToken } from './client'

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
  /** 取り消した日時。取り消していなければ null */
  withdrawnAt: string | null
  withdrawnById: number | null
  /** 取り消した管理者のユーザー名（取り消した時点の値）。取り消していなければ null */
  withdrawnByUsername: string | null
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

/** 取り消す（行は消さず、利用者側に出さなくなる）。取り消した後の1件を返す。すでに取り消していれば ApiError（409） */
export function withdrawAnnouncement(id: number, token: string): Promise<AdminAnnouncement> {
  return sendWithToken<AdminAnnouncement>('POST', `/api/admin/announcements/${id}/withdraw`, token)
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

// ---- 通報（docs/admin.md「通報」。措置の機能はまだなく、状態を記録するだけ） ----

export type AdminReportStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED'
export type AdminReportTargetType = 'POST' | 'USER' | 'MESSAGE'

/** 一覧の1行（対象ごと）。DM の本文は含まない */
export type AdminReportListItem = {
  caseId: number
  targetType: AdminReportTargetType
  targetId: number
  status: AdminReportStatus
  /** 投稿の題名・「@ユーザー名」・「@ユーザー名 のメッセージ」。証拠を消した後は null */
  summary: string | null
  reportCount: number
  /** 理由ごとの件数（多い順） */
  reasons: { reason: string; count: number }[]
  firstReportedAt: string
  lastReportedAt: string
  evidencePurged: boolean
}

export type AdminReportList = {
  items: AdminReportListItem[]
  page: number
  size: number
  hasNext: boolean
}

/** 通報した時点の内容のコピー（バックエンドの EvidenceSnapshot）。対象に応じて1つだけが入る */
export type AdminReportEvidence = {
  post: {
    postId: number
    authorId: number
    authorUsername: string
    title: string
    fashionCategory: string
    tags: string[]
    wornItems: string | null
    description: string
    referenceUrl: string | null
    createdAt: string
    editedAt: string | null
  } | null
  user: { userId: number; username: string; displayName: string } | null
  /** 通報されたメッセージと前後5件ずつ。reported が通報されたメッセージ */
  messages:
    | {
        messageId: number
        senderId: number
        senderUsername: string
        body: string | null
        hasImage: boolean
        sentAt: string
        reported: boolean
      }[]
    | null
}

export type AdminReportDetail = {
  caseId: number
  targetType: AdminReportTargetType
  targetId: number
  /** 通報された人（投稿者・ユーザー本人・メッセージを送った人）。措置で利用停止にする相手 */
  targetUserId: number
  status: AdminReportStatus
  reportCount: number
  firstReportedAt: string
  lastReportedAt: string
  closedAt: string | null
  handledByUsername: string | null
  adminMemo: string | null
  evidencePurged: boolean
  evidencePurgedAt: string | null
  /** 保存期間を過ぎて消した後は null */
  evidence: AdminReportEvidence | null
  /** 証拠の写真（中身は fetchEvidenceImage で取得する）。messageId は DM の画像のとき */
  evidenceFiles: { id: number; messageId: number | null }[]
  /** 対象の今の状態（EDITED は通報の後に編集された） */
  currentState: 'EXISTS' | 'EDITED' | 'DELETED'
  reports: { reporterId: number; reporterUsername: string; reason: string; comment: string | null; reportedAt: string }[]
}

export type AdminReportStatusResult = {
  caseId: number
  status: AdminReportStatus
  closedAt: string | null
  handledByUsername: string | null
  adminMemo: string | null
}

/** 通報の一覧（対象ごと、新しく通報された順）。status・targetType が空なら絞らない */
export function listReports(
  params: { status: string; targetType: string; page: number },
  token: string,
): Promise<AdminReportList> {
  const query = new URLSearchParams({ page: String(params.page), size: String(ADMIN_PAGE_SIZE) })
  if (params.status) query.set('status', params.status)
  if (params.targetType) query.set('targetType', params.targetType)
  return getJson<AdminReportList>(`/api/admin/reports?${query.toString()}`, token)
}

/** 通報の詳細。DM の通報では、開いたことが操作ログに記録される */
export function fetchReport(caseId: number, token: string): Promise<AdminReportDetail> {
  return getJson<AdminReportDetail>(`/api/admin/reports/${caseId}`, token)
}

/** 通報の状態を記録する（措置は行わない。措置は下の措置の API）。すでにその状態などは ApiError（409） */
export function changeReportStatus(
  caseId: number,
  status: AdminReportStatus,
  memo: string,
  token: string,
): Promise<AdminReportStatusResult> {
  return postJson<AdminReportStatusResult>(`/api/admin/reports/${caseId}/status`, { status, memo }, token)
}

/** 証拠の写真の API のパス（画像の控えを見分ける名前にも使う） */
export function evidenceImagePath(caseId: number, fileId: number): string {
  return `/api/admin/reports/${caseId}/evidence-files/${fileId}`
}

/**
 * 証拠の写真の中身（管理者向けの API から、認証つきで取得する）。
 * 証拠の写真は /uploads/** では配信しない。<img> のタグからは認証のヘッダーを付けられないため、取得した中身を Blob で返し、
 * 画面で一時的な URL（URL.createObjectURL）にして表示する（DM の画像と同じ部品。useAuthorizedImage）
 */
export function fetchEvidenceImage(caseId: number, fileId: number, token: string): Promise<Blob> {
  return getBlob(evidenceImagePath(caseId, fileId), token)
}

// ---- 措置（docs/admin.md「6. 措置」） ----

/** 措置の種類（バックエンドの ModerationActionType） */
export type ModerationActionType = 'HIDE_POST' | 'UNHIDE_POST' | 'SUSPEND_USER' | 'UNSUSPEND_USER'

/** 措置の履歴の1行（新しい順）。memo は管理者のメモ（管理者にだけ見せ、操作ログには書かない） */
export type ModerationHistoryItem = {
  id: number
  action: ModerationActionType
  reason: string | null
  reasonLabel: string | null
  suspendedUntil: string | null
  reportCaseId: number | null
  adminUsername: string
  memo: string | null
  createdAt: string
}

/** 投稿のいまの状態と措置の履歴 */
export type ModerationPostState = {
  postId: number
  title: string
  authorId: number
  authorUsername: string
  createdAt: string
  hidden: boolean
  hiddenAt: string | null
  hiddenReason: string | null
  hiddenReasonLabel: string | null
  history: ModerationHistoryItem[]
}

/** ユーザーのいまの状態と措置の履歴。期限を過ぎた利用停止は suspended が false */
export type ModerationUserState = {
  userId: number
  username: string
  /** 管理者は利用停止にできない */
  admin: boolean
  suspended: boolean
  suspendedAt: string | null
  /** 期限。無期限なら null */
  suspendedUntil: string | null
  suspensionReason: string | null
  suspensionReasonLabel: string | null
  history: ModerationHistoryItem[]
}

/**
 * 通報から措置するとき、reportCaseId にその通報を渡す（同じ対象の通報であること）。
 * resolveReport が true なら、同時に通報を「対応済み」にする
 */
export type HidePostRequest = { reason: string; memo: string; reportCaseId: number | null; resolveReport: boolean }

/** days は 1・3・7・30。無期限なら null */
export type SuspendUserRequest = {
  reason: string
  days: number | null
  memo: string
  reportCaseId: number | null
  resolveReport: boolean
}

export type HiddenPostList = {
  items: {
    postId: number
    title: string
    authorUsername: string
    hiddenAt: string
    hiddenReason: string
    hiddenReasonLabel: string
  }[]
  page: number
  size: number
  hasNext: boolean
}

export type SuspendedUserList = {
  items: {
    userId: number
    username: string
    suspendedAt: string
    suspendedUntil: string | null
    suspensionReason: string
    suspensionReasonLabel: string
  }[]
  page: number
  size: number
  hasNext: boolean
}

/** 投稿の状態。存在しない投稿は ApiError（404） */
export function fetchModerationPost(postId: number, token: string): Promise<ModerationPostState> {
  return getJson<ModerationPostState>(`/api/admin/moderation/posts/${postId}`, token)
}

/** 投稿を非公開にする。すでに非公開なら ApiError（409） */
export function hidePost(postId: number, request: HidePostRequest, token: string): Promise<ModerationPostState> {
  return postJson<ModerationPostState>(`/api/admin/moderation/posts/${postId}/hide`, request, token)
}

/** 非公開にした投稿を公開に戻す。非公開でなければ ApiError（409） */
export function unhidePost(postId: number, memo: string, token: string): Promise<ModerationPostState> {
  return postJson<ModerationPostState>(`/api/admin/moderation/posts/${postId}/unhide`, { memo }, token)
}

/** ユーザーの状態（ID から）。存在しないユーザーは ApiError（404） */
export function fetchModerationUser(userId: number, token: string): Promise<ModerationUserState> {
  return getJson<ModerationUserState>(`/api/admin/moderation/users/${userId}`, token)
}

/** ユーザーの状態（ユーザー名から。先頭の @ は付けても付けなくてもよい） */
export function findModerationUser(username: string, token: string): Promise<ModerationUserState> {
  return getJson<ModerationUserState>(
    `/api/admin/moderation/users?${new URLSearchParams({ username }).toString()}`,
    token,
  )
}

/** 利用停止にする。管理者・すでに利用停止中なら ApiError（409） */
export function suspendUser(userId: number, request: SuspendUserRequest, token: string): Promise<ModerationUserState> {
  return postJson<ModerationUserState>(`/api/admin/moderation/users/${userId}/suspend`, request, token)
}

/** 利用停止を解除する。利用停止中でなければ ApiError（409） */
export function unsuspendUser(userId: number, memo: string, token: string): Promise<ModerationUserState> {
  return postJson<ModerationUserState>(`/api/admin/moderation/users/${userId}/unsuspend`, { memo }, token)
}

/** 非公開にしている投稿（非公開にした日時の新しい順） */
export function listHiddenPosts(page: number, token: string): Promise<HiddenPostList> {
  return getJson<HiddenPostList>(`/api/admin/moderation/hidden-posts?page=${page}&size=${ADMIN_PAGE_SIZE}`, token)
}

/** いま利用停止中のユーザー（期限を過ぎたものは含まない） */
export function listSuspendedUsers(page: number, token: string): Promise<SuspendedUserList> {
  return getJson<SuspendedUserList>(`/api/admin/moderation/suspended-users?page=${page}&size=${ADMIN_PAGE_SIZE}`, token)
}

// ---- お問い合わせ（docs/admin.md「7. お問い合わせ」） ----

export type AdminContactStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED'
/** 受付の経路。SUSPENDED_APPEAL は利用停止中の申し立て */
export type AdminContactChannel = 'NORMAL' | 'SUSPENDED_APPEAL'

/** 一覧の1行。内容・メールアドレスは含まない（詳細でだけ出す） */
export type AdminContactListItem = {
  id: number
  channel: AdminContactChannel
  category: string
  status: AdminContactStatus
  userId: number
  /** 送った人の今のユーザー名。退会していれば null */
  username: string | null
  createdAt: string
  closedAt: string | null
}

export type AdminContactList = {
  items: AdminContactListItem[]
  page: number
  size: number
  hasNext: boolean
}

export type AdminContactDetail = {
  id: number
  channel: AdminContactChannel
  category: string
  status: AdminContactStatus
  userId: number
  username: string | null
  /** 別の連絡先（入力されたときだけ。登録済みのアドレスと同じ・未入力なら null） */
  email: string | null
  body: string
  createdAt: string
  closedAt: string | null
  handledByUsername: string | null
  adminMemo: string | null
}

export type AdminContactStatusResult = {
  id: number
  status: AdminContactStatus
  closedAt: string | null
  handledByUsername: string | null
  adminMemo: string | null
}

/** お問い合わせの一覧（新しい順）。空の絞り込みは送らない */
export function listContacts(
  params: { status: string; category: string; channel: string; page: number },
  token: string,
): Promise<AdminContactList> {
  const query = new URLSearchParams({ page: String(params.page), size: String(ADMIN_PAGE_SIZE) })
  if (params.status) query.set('status', params.status)
  if (params.category) query.set('category', params.category)
  if (params.channel) query.set('channel', params.channel)
  return getJson<AdminContactList>(`/api/admin/contacts?${query.toString()}`, token)
}

/** お問い合わせの詳細。開いたことは操作ログに残らない（docs/admin.md「7. お問い合わせ」） */
export function fetchContact(id: number, token: string): Promise<AdminContactDetail> {
  return getJson<AdminContactDetail>(`/api/admin/contacts/${id}`, token)
}

/** お問い合わせの状態を記録する。すでにその状態なら ApiError（409） */
export function changeContactStatus(
  id: number,
  status: AdminContactStatus,
  memo: string,
  token: string,
): Promise<AdminContactStatusResult> {
  return postJson<AdminContactStatusResult>(`/api/admin/contacts/${id}/status`, { status, memo }, token)
}
