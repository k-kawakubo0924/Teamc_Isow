import { Navigate, Outlet } from 'react-router'
import { useAuth } from './authContext'

/**
 * ログインが必要な画面。未ログインならログイン画面へリダイレクトする。
 * 利用停止になった（API が利用停止を返した）ら、利用停止の画面へ移る（docs/admin.md「6. 措置」）
 */
export function RequireAuth() {
  const { isLoggedIn, suspension } = useAuth()
  if (suspension) return <Navigate to="/suspended" replace />
  return isLoggedIn ? <Outlet /> : <Navigate to="/login" replace />
}

/**
 * 未ログインのときだけ開ける画面（ログイン・新規会員登録）。ログイン済みならホーム画面へリダイレクトする。
 * ログインで利用停止が返ったら、利用停止の画面へ移る
 */
export function GuestOnly() {
  const { isLoggedIn, suspension } = useAuth()
  if (suspension) return <Navigate to="/suspended" replace />
  return isLoggedIn ? <Navigate to="/" replace /> : <Outlet />
}
