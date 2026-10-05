import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import type { TimelineTab } from '../../api/posts'
import { useAuth } from '../../auth/authContext'
import { loadColumns, saveColumns, type Columns } from './columnSetting'
import { PostCard } from './PostCard'
import { useReactions } from './useReactions'
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

const COLUMN_OPTIONS: { columns: Columns; label: string }[] = [
  { columns: 1, label: '1列で表示' },
  { columns: 2, label: '2列で表示' },
  { columns: 3, label: '3列で表示' },
]

/** いいね・お気に入りに失敗したときの通知を表示しておく時間 */
const TOAST_DURATION_MS = 4000

/**
 * ホーム画面（docs/home.md、design/home.png）。
 * 新着メッセージの欄は DM 機能で追加する。
 */
function HomePage() {
  const [tab, setTab] = useState<HomeTab>('recommended')
  const [columns, setColumns] = useState<Columns>(loadColumns)

  const handleChangeColumns = (next: Columns) => {
    setColumns(next)
    saveColumns(next)
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
      </header>

      <div className="home-toolbar">
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
        {/* 表示列の切り替え（design/home.png 右上のアイコン）。選んだ列は端末ごとに記憶する */}
        <div className="home-columns" role="group" aria-label="表示列">
          {COLUMN_OPTIONS.map((option) => (
            <button
              key={option.columns}
              type="button"
              className={`home-columns-button${columns === option.columns ? ' home-columns-active' : ''}`}
              aria-label={option.label}
              aria-pressed={columns === option.columns}
              onClick={() => handleChangeColumns(option.columns)}
            >
              <ColumnsIcon columns={option.columns} />
            </button>
          ))}
        </div>
      </div>

      <section id="home-tab-panel" role="tabpanel" aria-labelledby={`home-tab-${tab}`} className="home-panel">
        {tab === 'following' ? (
          // フォロー機能はプロフィール機能で作る。それまでは API を呼ばずにこの表示にする
          <EmptyMessage title="フォロー中のユーザーがいません" />
        ) : (
          // タブを切り替えたら一覧を作り直す（読み込み中の通信は中断され、前のタブの結果は表示されない）
          <Timeline key={tab} tab={tab} columns={columns} />
        )}
      </section>
    </main>
  )
}

/** おすすめ・新着の一覧。末尾が見えてきたら次の20件を読み込む（無限スクロール） */
function Timeline({ tab, columns }: { tab: TimelineTab; columns: Columns }) {
  const { token } = useAuth()
  const { state, loadMore, retry, updatePost } = useTimeline(tab, token)
  const [toast, setToast] = useState<string | null>(null)
  const { toggle } = useReactions(token, updatePost, setToast)
  const sentinelRef = useRef<HTMLDivElement>(null)

  // いいね・お気に入りに失敗したときの通知は、しばらくしたら消す
  useEffect(() => {
    if (toast === null) return
    const timer = setTimeout(() => setToast(null), TOAST_DURATION_MS)
    return () => clearTimeout(timer)
  }, [toast])

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
        <ul className={`home-grid home-grid-${columns}`}>
          {state.posts.map((post) => (
            <li key={post.id}>
              <PostCard
                post={post}
                // 1列・3列のときは写真のみ（docs/home.md・docs/profile.md）
                detailed={columns === 2}
                onToggleLike={() => toggle(post, 'like')}
                onToggleFavorite={() => toggle(post, 'favorite')}
              />
            </li>
          ))}
        </ul>
      )}

      {toast && (
        <div className="home-toast" role="alert">
          {toast}
        </div>
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

/** 表示列のアイコン（列の数だけ縦長の四角を並べる） */
function ColumnsIcon({ columns }: { columns: Columns }) {
  const gap = 1.5
  const width = (14 - gap * (columns - 1)) / columns
  return (
    <svg width="16" height="16" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.2" aria-hidden="true">
      {Array.from({ length: columns }, (_, i) => (
        <rect key={i} x={1 + i * (width + gap)} y="1.5" width={width} height="13" rx="0.8" />
      ))}
    </svg>
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
