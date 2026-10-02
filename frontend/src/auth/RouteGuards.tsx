import { Navigate, Outlet } from 'react-router'
import { useAuth } from './authContext'

/** ログインが必要な画面。未ログインならログイン画面へリダイレクトする */
export function RequireAuth() {
  const { isLoggedIn } = useAuth()
  return isLoggedIn ? <Outlet /> : <Navigate to="/login" replace />
}

/** 未ログインのときだけ開ける画面（ログイン・新規会員登録）。ログイン済みならホーム画面へリダイレクトする */
export function GuestOnly() {
  const { isLoggedIn } = useAuth()
  return isLoggedIn ? <Navigate to="/" replace /> : <Outlet />
}
