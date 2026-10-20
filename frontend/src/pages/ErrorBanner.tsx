import { useEffect, useRef } from 'react'

/**
 * 画面上部に出す警告文（design/Newregistrationerror.png・Loginauthenticationfailed.png）。
 * scrollIntoView を付けると、出たときに警告文が見える位置まで画面を動かす（押したボタンが下にあり、
 * スマホでキーボードが出ていると、上の警告文が画面の外に出たままになるため。docs/auth.md「ログインの失敗の回数の上限」）
 */
export function ErrorBanner({ message, scrollIntoView = false }: { message: string; scrollIntoView?: boolean }) {
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (scrollIntoView) ref.current?.scrollIntoView?.({ block: 'nearest' })
  }, [message, scrollIntoView])

  return (
    <div className="auth-banner" role="alert" ref={ref}>
      <span className="auth-banner-icon" aria-hidden="true">
        !
      </span>
      <p>{message}</p>
    </div>
  )
}
