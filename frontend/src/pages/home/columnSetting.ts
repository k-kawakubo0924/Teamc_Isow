// ホームの表示列（1〜3列）の設定の保存と読み込み（docs/home.md「表示列の切り替え」、暫定）。
//
// 端末ごとに localStorage に保存する。見やすい列数はスマホと PC など画面の大きさで変わるため、
// サーバーには保存しない（全端末で同じになるうえ、API とテーブルも必要になるため）。
// プライベートブラウズなどで保存・読み込みができない場合はエラーにせず、既定の2列で表示する。

export type Columns = 1 | 2 | 3

export const DEFAULT_COLUMNS: Columns = 2

const STORAGE_KEY = 'isho.home.columns'

export function loadColumns(): Columns {
  try {
    const stored = Number(localStorage.getItem(STORAGE_KEY))
    return stored === 1 || stored === 2 || stored === 3 ? stored : DEFAULT_COLUMNS
  } catch {
    return DEFAULT_COLUMNS
  }
}

export function saveColumns(columns: Columns): void {
  try {
    localStorage.setItem(STORAGE_KEY, String(columns))
  } catch {
    // 保存できなくても、この画面を開いている間は選んだ列で表示できるため何もしない
  }
}
