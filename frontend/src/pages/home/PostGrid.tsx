import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useAuth } from '../../auth/authContext'
import type { Columns } from './columnSetting'
import { PostCard } from './PostCard'
import { usePostPages, type FetchPostPage } from './usePostPages'
import { useReactions } from './useReactions'
import './home.css'

/** 一覧の末尾がこの距離（px）まで近づいたら、次のページを読み込み始める */
const PRELOAD_MARGIN_PX = 400

/** いいね・お気に入りに失敗したときの通知を表示しておく時間 */
const TOAST_DURATION_MS = 4000

const COLUMN_OPTIONS: { columns: Columns; label: string }[] = [
  { columns: 1, label: '1列で表示' },
  { columns: 2, label: '2列で表示' },
  { columns: 3, label: '3列で表示' },
]

/**
 * 投稿のグリッド（ホームの一覧・プロフィールの投稿一覧で共通。design/home.png のカード）。
 * 末尾が見えてきたら次のページを読み込む（無限スクロール）。いいね・お気に入りはその場で押せる。
 * 読み込む一覧を変えるときは key を変えて作り直すこと（fetchPage は最初に渡されたものを使い続ける）
 *
 * @param empty 投稿が1件もないときに表示する内容
 */
export function PostGrid({ fetchPage, columns, empty }: { fetchPage: FetchPostPage; columns: Columns; empty: ReactNode }) {
  const { token } = useAuth()
  const { state, loadMore, retry, updatePost } = usePostPages(fetchPage, token)
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
        empty
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

/** 表示列の切り替え（design/home.png・myprofile.png 右上のアイコン） */
export function ColumnsSwitcher({ columns, onChange }: { columns: Columns; onChange: (columns: Columns) => void }) {
  return (
    <div className="home-columns" role="group" aria-label="表示列">
      {COLUMN_OPTIONS.map((option) => (
        <button
          key={option.columns}
          type="button"
          className={`home-columns-button${columns === option.columns ? ' home-columns-active' : ''}`}
          aria-label={option.label}
          aria-pressed={columns === option.columns}
          onClick={() => onChange(option.columns)}
        >
          <ColumnsIcon columns={option.columns} />
        </button>
      ))}
    </div>
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

/** 一覧が空のときの表示 */
export function EmptyMessage({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="home-empty">
      <p className="home-empty-title">{title}</p>
      {children}
    </div>
  )
}
