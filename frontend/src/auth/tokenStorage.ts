// ログイン時に受け取ったトークンの保存・取得・削除。
//
// 保存場所は localStorage。選んだ理由:
// - 再読み込みやブラウザ再起動後も、有効期限（24時間）までログイン状態を保てる
// - 複数タブで同じログイン状態を共有できる（タブごとにログインし直さなくてよい）
// - バックエンドの Authorization: Bearer 方式をそのまま使える
// 弱点は XSS でトークンを読み取られうること。そのため dangerouslySetInnerHTML は使わない（CLAUDE.md）。
// 保存場所を変えるときは、このファイルだけを直せばよいようにしている。

/** 他のタブでの変更（storage イベント）を見分けるためにも使う */
export const TOKEN_STORAGE_KEY = 'isho.auth'

export type AuthSession = {
  token: string
  /** 有効期限（ミリ秒のUNIX時刻） */
  expiresAt: number
}

export function saveToken(token: string, expiresAt: string): void {
  localStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify({ token, expiresAt }))
}

/** 保存したトークンと有効期限を返す。未保存・期限切れ・壊れた値の場合は null を返す（期限切れ・壊れた値は削除する） */
export function getSession(): AuthSession | null {
  const raw = localStorage.getItem(TOKEN_STORAGE_KEY)
  if (raw === null) return null

  try {
    const stored = JSON.parse(raw) as { token?: unknown; expiresAt?: unknown }
    const expiresAt = typeof stored.expiresAt === 'string' ? Date.parse(stored.expiresAt) : NaN
    if (typeof stored.token === 'string' && !Number.isNaN(expiresAt) && expiresAt > Date.now()) {
      return { token: stored.token, expiresAt }
    }
  } catch {
    // JSON として読めない値は捨てる
  }
  clearToken()
  return null
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
}
