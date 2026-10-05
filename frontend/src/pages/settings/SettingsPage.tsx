import { useEffect, useState, type ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { useAuth } from '../../auth/authContext'
import { goBack } from './goBack'
import './settings.css'

/** 「準備中です」を表示しておく時間（ミリ秒） */
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
  // 消えるときは文字を残したまま薄くする（先に文字を消すと、空の枠が一瞬見えるため）
  const [notice, setNotice] = useState({ text: '', visible: false, shownAt: 0 })

  useEffect(() => {
    if (!notice.visible) return
    const timer = setTimeout(() => setNotice((prev) => ({ ...prev, visible: false })), NOTICE_DURATION_MS)
    return () => clearTimeout(timer)
  }, [notice.visible, notice.shownAt])

  // パスワード再設定・メールアドレス再設定は、画面ができるまで「準備中です」と表示するだけ。
  // 続けて押したときも表示時間を延ばすよう、押した時刻を持つ
  const showComingSoon = () => setNotice({ text: '準備中です', visible: true, shownAt: Date.now() })

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
          onClick={showComingSoon}
        />
        <MenuItem
          label="メールアドレス再設定"
          icon={
            <svg {...ICON_PROPS}>
              <rect x="3.5" y="5.5" width="17" height="13" rx="1.5" />
              <path d="m4 7 8 6 8-6" />
            </svg>
          }
          onClick={showComingSoon}
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
