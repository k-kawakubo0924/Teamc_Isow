// 投稿一覧の表示列（1〜3列）の設定の保存と読み込み（docs/home.md「表示列の切り替え」、暫定）。
//
// 端末ごとに localStorage に保存する。見やすい列数はスマホと PC など画面の大きさで変わるため、
// サーバーには保存しない（全端末で同じになるうえ、API とテーブルも必要になるため）。
// 画面ごと（ホーム・プロフィール・検索結果）に別々に覚える。画面画像の既定の列数が違うため。
// プライベートブラウズなどで保存・読み込みができない場合はエラーにせず、既定の列で表示する。

export type Columns = 1 | 2 | 3

export type ColumnSetting = {
  storageKey: string
  defaultColumns: Columns
}

/** ホーム（design/home.png は2列） */
export const HOME_COLUMNS: ColumnSetting = { storageKey: 'isho.home.columns', defaultColumns: 2 }

/** プロフィール（design/myprofile.png・otherprofile.png は3列） */
export const PROFILE_COLUMNS: ColumnSetting = { storageKey: 'isho.profile.columns', defaultColumns: 3 }

/** 検索結果（design/Searchresults.png は2列） */
export const SEARCH_COLUMNS: ColumnSetting = { storageKey: 'isho.search.columns', defaultColumns: 2 }

export function loadColumns(setting: ColumnSetting): Columns {
  try {
    const stored = Number(localStorage.getItem(setting.storageKey))
    return stored === 1 || stored === 2 || stored === 3 ? stored : setting.defaultColumns
  } catch {
    return setting.defaultColumns
  }
}

export function saveColumns(setting: ColumnSetting, columns: Columns): void {
  try {
    localStorage.setItem(setting.storageKey, String(columns))
  } catch {
    // 保存できなくても、この画面を開いている間は選んだ列で表示できるため何もしない
  }
}
