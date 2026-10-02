import type { ReactNode } from 'react'
import { NavLink, Outlet } from 'react-router'
import './BottomNav.css'

type NavItem = {
  label: string
  /** 画面がまだない項目は undefined（押せない状態で表示する） */
  to?: string
  icon: ReactNode
}

// アイコンは線だけの簡単な SVG（design/Post screen.png の下部ナビゲーション）
const ICON_PROPS = {
  width: 22,
  height: 22,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.6,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const

// 検索・DM・プロフィールは、各機能の担当者が画面を作ったら to を設定する
const ITEMS: NavItem[] = [
  {
    label: 'ホーム',
    to: '/',
    icon: (
      <svg {...ICON_PROPS}>
        <path d="M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1z" />
      </svg>
    ),
  },
  {
    label: '検索',
    icon: (
      <svg {...ICON_PROPS}>
        <circle cx="11" cy="11" r="6.5" />
        <path d="m20 20-4.2-4.2" />
      </svg>
    ),
  },
  {
    label: '投稿',
    to: '/post',
    icon: (
      <svg {...ICON_PROPS}>
        <rect x="4" y="4" width="16" height="16" rx="3" />
        <path d="M12 8.5v7M8.5 12h7" />
      </svg>
    ),
  },
  {
    label: 'DM',
    icon: (
      <svg {...ICON_PROPS}>
        <path d="M20 12a8 8 0 0 1-11.8 7L4 20l1.1-3.9A8 8 0 1 1 20 12z" />
      </svg>
    ),
  },
  {
    label: 'プロフィール',
    icon: (
      <svg {...ICON_PROPS}>
        <circle cx="12" cy="8.5" r="3.8" />
        <path d="M4.5 20.5c1.2-3.6 4-5.5 7.5-5.5s6.3 1.9 7.5 5.5" />
      </svg>
    ),
  },
]

/** 画面下部のナビゲーション（ホーム / 検索 / 投稿 / DM / プロフィール） */
export function BottomNav() {
  return (
    <nav className="bottom-nav" aria-label="メインメニュー">
      <ul className="bottom-nav-list">
        {ITEMS.map((item) => (
          <li key={item.label}>
            {item.to ? (
              <NavLink
                to={item.to}
                end
                className={({ isActive }) => `bottom-nav-item${isActive ? ' bottom-nav-item-active' : ''}`}
              >
                {item.icon}
                <span>{item.label}</span>
              </NavLink>
            ) : (
              <span className="bottom-nav-item bottom-nav-item-disabled" aria-disabled="true" title="準備中">
                {item.icon}
                <span>{item.label}</span>
              </span>
            )}
          </li>
        ))}
      </ul>
    </nav>
  )
}

/**
 * 下部ナビゲーションを表示する画面の共通レイアウト（App.tsx のルートで使う）。
 * 投稿作成のように、画面下部に別のボタンを置く画面ではこのレイアウトを使わない
 */
export function TabLayout() {
  return (
    <div className="tab-layout">
      <Outlet />
      <BottomNav />
    </div>
  )
}
