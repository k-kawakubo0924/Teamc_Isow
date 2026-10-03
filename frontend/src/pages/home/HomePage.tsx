import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import type { TimelineTab } from '../../api/posts'
import { useAuth } from '../../auth/authContext'
import { PostCard } from './PostCard'
import { useTimeline } from './useTimeline'
import './home.css'

type HomeTab = 'recommended' | 'following' | 'latest'

const TABS: { key: HomeTab; label: string }[] = [
  { key: 'recommended', label: 'おすすめ' },
  { key: 'following', label: 'フォロー中' },
  { key: 'latest', label: '新着' },
]

/** 一覧の末尾がこの距離（px）まで近づいたら、次のページを読み込み始める */
const PRELOAD_MARGIN_PX = 400

/**
 * ホーム画面（docs/home.md、design/home.png）。
 * 新着メッセージの欄は DM 機能で、表示列の切り替え（1〜3列）は別途追加する（現在は2列で固定）。
 */
function HomePage() {
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [tab, setTab] = useState<HomeTab>('recommended')

  // 【仮置き】詳細設定の画面ができたら、ログアウトボタンと一緒に削除する
  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <main className="home-page">
      <header className="home-header">
        {/* 通知一覧（docs/notification.md）ができるまでは表示のみ */}
        <span className="home-bell" role="img" aria-label="通知">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" aria-hidden="true">
            <path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2H4.5z" />
            <path d="M10 20.5a2 2 0 0 0 4 0" strokeLinecap="round" />
          </svg>
        </span>
        <h1 className="home-logo">ISHO</h1>
        {/*
          【仮置き】ログアウトボタン。デザイン画像には無い。
          仕様上の置き場所は詳細設定のメニュー（docs/settings.md）で、その画面がまだ無いため、
          画面からログアウトできるよう一時的にここに置いている。
          プロフィール機能の担当者が詳細設定を作ったら、このボタンと handleLogout・.home-logout のスタイルを削除すること。
        */}
        <button type="button" className="home-logout" onClick={handleLogout}>
          ログアウト
        </button>
      </header>

      <div className="home-tabs" role="tablist" aria-label="表示する投稿">
        {TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            id={`home-tab-${item.key}`}
            aria-selected={tab === item.key}
            aria-controls="home-tab-panel"
            className={`home-tab${tab === item.key ? ' home-tab-active' : ''}`}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <section id="home-tab-panel" role="tabpanel" aria-labelledby={`home-tab-${tab}`} className="home-panel">
        {tab === 'following' ? (
          // フォロー機能はプロフィール機能で作る。それまでは API を呼ばずにこの表示にする
          <EmptyMessage title="フォロー中のユーザーがいません" />
        ) : (
          // タブを切り替えたら一覧を作り直す（読み込み中の通信は中断され、前のタブの結果は表示されない）
          <Timeline key={tab} tab={tab} />
        )}
      </section>
    </main>
  )
}

/** おすすめ・新着の一覧。末尾が見えてきたら次の20件を読み込む（無限スクロール） */
function Timeline({ tab }: { tab: TimelineTab }) {
  const { token } = useAuth()
  const { state, loadMore, retry } = useTimeline(tab, token)
  const sentinelRef = useRef<HTMLDivElement>(null)

  // 末尾の目印が画面に近づいたら読み込む。最初の1ページもこの仕組みで読み込む。
  // 読み込みのたびに作り直し、まだ画面が埋まっていなければ続けて次のページを読み込む
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) void loadMore()
      },
      { rootMargin: `0px 0px ${PRELOAD_MARGIN_PX}px 0px` },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [loadMore])

  const isEmpty = state.posts.length === 0 && !state.hasNext && state.status === 'idle'

  return (
    <>
      {isEmpty ? (
        <EmptyMessage title="まだ投稿がありません">
          <Link to="/post" className="home-empty-action">
            投稿する
          </Link>
        </EmptyMessage>
      ) : (
        <ul className="home-grid">
          {state.posts.map((post) => (
            <li key={post.id}>
              <PostCard post={post} />
            </li>
          ))}
        </ul>
      )}

      {state.status === 'loading' && (
        <p className="home-status" role="status">
          読み込み中...
        </p>
      )}
      {state.status === 'error' && (
        <div className="home-status" role="alert">
          <p>{state.errorMessage}</p>
          <button type="button" className="home-retry" onClick={retry}>
            もう一度読み込む
          </button>
        </div>
      )}
      {/* 次のページがある間だけ置く、無限スクロールの目印 */}
      {state.hasNext && <div ref={sentinelRef} className="home-sentinel" aria-hidden="true" />}
    </>
  )
}

function EmptyMessage({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="home-empty">
      <p className="home-empty-title">{title}</p>
      {children}
    </div>
  )
}

export default HomePage
