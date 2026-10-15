import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { setSuspendedHandler, setUnauthorizedHandler, type SuspensionInfo } from '../api/client'
import { AuthContext, type AuthContextValue } from './authContext'
import { clearToken, getSession, saveToken, TOKEN_STORAGE_KEY, type AuthSession } from './tokenStorage'

/** setTimeout に渡せる最大の待ち時間（約24.8日） */
const MAX_TIMEOUT_MS = 2 ** 31 - 1

/**
 * ログイン状態をアプリ全体に提供する。
 * - 起動時（再読み込み時）は保存済みのトークンから状態を復元する
 * - 有効期限になったら自動でログアウトする
 * - トークン付きの API が 401 を返したらログアウトする
 * - 他のタブでのログイン・ログアウトを反映する
 * - API が利用停止を返したら、ログアウトして、利用停止の画面へ移る（docs/admin.md「6. 措置」。RouteGuards が移す）
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthSession | null>(() => getSession())
  const [suspension, setSuspension] = useState<SuspensionInfo | null>(null)

  const login = useCallback((token: string, expiresAt: string) => {
    saveToken(token, expiresAt)
    setSession(getSession())
  }, [])

  const logout = useCallback(() => {
    clearToken()
    setSession(null)
  }, [])

  // 有効期限になったらログアウトする（画面を開いたまま期限を迎えた場合）
  useEffect(() => {
    if (session === null) return
    const timer = setTimeout(logout, Math.min(session.expiresAt - Date.now(), MAX_TIMEOUT_MS))
    return () => clearTimeout(timer)
  }, [session, logout])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    return () => setUnauthorizedHandler(null)
  }, [logout])

  useEffect(() => {
    setSuspendedHandler((info) => {
      logout()
      setSuspension(info)
    })
    return () => setSuspendedHandler(null)
  }, [logout])

  const clearSuspension = useCallback(() => setSuspension(null), [])

  useEffect(() => {
    const handleStorage = (e: StorageEvent) => {
      // e.key が null になるのは localStorage.clear() のとき
      if (e.key === TOKEN_STORAGE_KEY || e.key === null) {
        setSession(getSession())
      }
    }
    window.addEventListener('storage', handleStorage)
    return () => window.removeEventListener('storage', handleStorage)
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({
      token: session?.token ?? null,
      isLoggedIn: session !== null,
      login,
      logout,
      suspension,
      clearSuspension,
    }),
    [session, login, logout, suspension, clearSuspension],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
