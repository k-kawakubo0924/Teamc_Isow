import { useEffect, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { useAuth } from '../../auth/authContext'
import { goBack } from './goBack'
import './settings.css'

/** 知らせ（パスワードを変更した など）を表示しておく時間（ミリ秒） */
const NOTICE_DURATION_MS = 2000

const ICON_PROPS = {
  width: 18,
  height: 18,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.6,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const

/**
 * 詳細設定（docs/settings.md、design/DetailedSettings.png）。
 * 自分のプロフィール右上の三点リーダーから開く（プロフィール画面ができるまでは、下部ナビゲーションの「プロフィール」から開く）
 */
function SettingsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { logout } = useAuth()
  // 消えるときは文字を残したまま薄くする（先に文字を消すと、空の枠が一瞬見えるため）。
  // ほかの画面から知らせ（state.notice）を受け取ったら、開いたときに出す
  const [notice, setNotice] = useState(() => {
    const text = (location.state as { notice?: string } | null)?.notice
    return typeof text === 'string' ? { text, visible: true, shownAt: Date.now() } : { text: '', visible: false, shownAt: 0 }
  })

  useEffect(() => {
    if (!notice.visible) return
    const timer = setTimeout(() => setNotice((prev) => ({ ...prev, visible: false })), NOTICE_DURATION_MS)
    return () => clearTimeout(timer)
  }, [notice.visible, notice.shownAt])


  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <main className="settings-page">
      <header className="settings-header">
        <button type="button" className="settings-back" aria-label="戻る" onClick={() => goBack(navigate, location, '/')}>
          ‹
        </button>
        <h1 className="settings-title">詳細設定</h1>
      </header>

      <ul className="settings-menu">
        <MenuItem
          label="プロフィール設定"
          icon={
            <svg {...ICON_PROPS}>
              <circle cx="12" cy="8.5" r="3.5" />
              <path d="M5 20c1.1-3.4 3.7-5 7-5s5.9 1.6 7 5" />
            </svg>
          }
          onClick={() => navigate('/settings/profile')}
        />
        <MenuItem
          label="パスワード再設定"
          icon={
            <svg {...ICON_PROPS}>
              <rect x="5" y="11" width="14" height="9" rx="1.5" />
              <path d="M8 11V8a4 4 0 0 1 8 0v3" />
            </svg>
          }
          onClick={() => navigate('/settings/password')}
        />
        <MenuItem
          label="メールアドレス再設定"
          icon={
            <svg {...ICON_PROPS}>
              <rect x="3.5" y="5.5" width="17" height="13" rx="1.5" />
              <path d="m4 7 8 6 8-6" />
            </svg>
          }
          onClick={() => navigate('/settings/email')}
        />
        {/* ブロックした相手の一覧と解除（docs/profile.md「ブロック」）。画面画像にはない項目 */}
        <MenuItem
          label="ブロック中のアカウント"
          icon={
            <svg {...ICON_PROPS}>
              <circle cx="12" cy="12" r="8" />
              <path d="m6.5 6.5 11 11" />
            </svg>
          }
          onClick={() => navigate('/settings/blocks')}
        />
        {/* お問い合わせ（docs/settings.md「お問い合わせ」）。画面画像にはない項目 */}
        <MenuItem
          label="お問い合わせ"
          icon={
            <svg {...ICON_PROPS}>
              <path d="M5 5.5h14v10H10l-4 3.5v-3.5H5z" />
              <path d="M9 9.5h6M9 12h4" />
            </svg>
          }
          onClick={() => navigate('/settings/contact')}
        />
      </ul>

      <ul className="settings-menu">
        <MenuItem
          label="ログアウト"
          danger
          icon={
            <svg {...ICON_PROPS}>
              <path d="M10 5H5.5v14H10" />
              <path d="M14 8.5 17.5 12 14 15.5M17.5 12H9" />
            </svg>
          }
          onClick={handleLogout}
        />
      </ul>

      {/* 読み上げソフトにも伝わるよう、常に置いておき中身だけを変える */}
      <p className="settings-notice" role="status" data-visible={notice.visible}>
        {notice.text}
      </p>
    </main>
  )
}

/** danger はログアウト（矢印を出さず、文字の色を変える） */
function MenuItem({
  label,
  icon,
  danger = false,
  onClick,
}: {
  label: string
  icon: ReactNode
  danger?: boolean
  onClick: () => void
}) {
  return (
    <li>
      <button type="button" className={`settings-item${danger ? ' settings-item-danger' : ''}`} onClick={onClick}>
        <span className="settings-item-icon">{icon}</span>
        <span className="settings-item-label">{label}</span>
        {!danger && (
          <span className="settings-item-arrow" aria-hidden="true">
            ›
          </span>
        )}
      </button>
    </li>
  )
}

export default SettingsPage
