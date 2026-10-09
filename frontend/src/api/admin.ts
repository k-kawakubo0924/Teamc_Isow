import { getJson } from './client'

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
