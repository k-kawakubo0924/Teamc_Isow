// ログイン時に受け取ったトークンの保存・取得・削除。
// 保存場所はステップ7で見直す前提で、仮に localStorage を使っている。
// 保存場所を変えるときは、このファイルだけを直せばよいようにしている。

const STORAGE_KEY = 'isho.auth'

type StoredToken = {
  token: string
  expiresAt: string
}

export function saveToken(token: string, expiresAt: string): void {
  const value: StoredToken = { token, expiresAt }
  localStorage.setItem(STORAGE_KEY, JSON.stringify(value))
}

/** 保存したトークンを返す。未保存・期限切れ・壊れた値の場合は null を返す（期限切れ・壊れた値は削除する） */
export function getToken(): string | null {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (raw === null) return null

  try {
    const stored = JSON.parse(raw) as Partial<StoredToken>
    const expiresAt = typeof stored.expiresAt === 'string' ? Date.parse(stored.expiresAt) : NaN
    if (typeof stored.token === 'string' && !Number.isNaN(expiresAt) && expiresAt > Date.now()) {
      return stored.token
    }
  } catch {
    // JSON として読めない値は捨てる
  }
  clearToken()
  return null
}

export function clearToken(): void {
  localStorage.removeItem(STORAGE_KEY)
}
