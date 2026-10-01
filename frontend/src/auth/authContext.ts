import { createContext, useContext } from 'react'

export type AuthContextValue = {
  /** API の Authorization ヘッダーに付けるトークン。未ログインなら null */
  token: string | null
  isLoggedIn: boolean
  /** ログインAPIの結果を保存し、ログイン状態にする */
  login: (token: string, expiresAt: string) => void
  /** トークンを破棄し、未ログイン状態にする（画面遷移は呼び出し側で行う） */
  logout: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

/** ログイン状態を使う。AuthProvider の内側でのみ呼べる */
export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (value === null) {
    throw new Error('useAuth は AuthProvider の内側で使ってください')
  }
  return value
}
