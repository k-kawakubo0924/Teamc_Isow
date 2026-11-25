import { getJson } from './client'

/** DB のマスタの選択肢。保存するときは id を送る */
export type MasterOption = {
  id: number
  name: string
}

/** enum の選択肢。保存するときは code を送る。label は表示専用 */
export type EnumOption = {
  code: string
  label: string
}

/** バックエンドの MastersResponse。各一覧は表示する順に並んでいる */
export type MastersResponse = {
  fashionCategories: MasterOption[]
  bodyTypes: MasterOption[]
  personalColors: MasterOption[]
  /** 公式タグ */
  tags: MasterOption[]
  ageGroups: EnumOption[]
  genders: EnumOption[]
}

/** 画面の選択肢をまとめて取得する（GET /api/masters） */
export function fetchMasters(token: string): Promise<MastersResponse> {
  return getJson<MastersResponse>('/api/masters', token)
}
