import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client'
import { fetchPost, type PostResponse } from '../../api/posts'
import { useAuth } from '../../auth/authContext'
import { formatHeight } from '../../utils/height'
import { HeartIcon, StarIcon } from '../home/PostCard'
import { useReactions, type ReactionTarget } from '../home/useReactions'
import { goBack } from '../settings/goBack'
import { PostPhotoViewer } from './PostPhotoViewer'
import './postDetail.css'

/** いいね・お気に入りに失敗したときの通知を表示しておく時間（ホームの一覧と同じ） */
const TOAST_DURATION_MS = 4000

type State =
  | { phase: 'loading' }
  | { phase: 'ready'; post: PostResponse }
  | { phase: 'notFound' }
  | { phase: 'error'; message: string }

/**
 * 投稿の詳細（/posts/{id}。docs/post.md「投稿の詳細画面」）。
 * 詳細画面の画面画像はないため、項目の並びは投稿の確認画面（design/Check post content.png）に合わせている。
 * 自分の投稿と他人の投稿で表示は変えない（編集・削除は docs/post.md で未確定）
 */
function PostDetailPage() {
  const { postId } = useParams()
  const id = postId !== undefined && /^\d+$/.test(postId) ? Number(postId) : null
  // 別の投稿に移ったら、読み込んだ内容・写真の表示位置を作り直す
  return <PostDetailView key={id ?? 'invalid'} postId={id} />
}

function PostDetailView({ postId }: { postId: number | null }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  // ID が数字でなければ、API を呼ばずに「投稿が見つかりません」を出す
  const [state, setState] = useState<State>(postId === null ? { phase: 'notFound' } : { phase: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    if (postId === null || !token) return
    const controller = new AbortController()
    fetchPost(postId, token, controller.signal)
      .then((post) => setState({ phase: 'ready', post }))
      .catch((err: unknown) => {
        if (controller.signal.aborted) return
        // 存在しない（削除された）投稿と、通信の失敗を分けて表示する
        if (err instanceof ApiError && err.status === 404) {
          setState({ phase: 'notFound' })
        } else {
          setState({ phase: 'error', message: err instanceof Error ? err.message : String(err) })
        }
      })
    return () => controller.abort()
  }, [postId, token, reloadKey])

  useEffect(() => {
    if (toast === null) return
    const timer = setTimeout(() => setToast(null), TOAST_DURATION_MS)
    return () => clearTimeout(timer)
  }, [toast])

  // いいね・お気に入りはホームの一覧と同じ処理（連打への対策・失敗したら元に戻す）
  const updatePost = useCallback((_id: number, patch: Partial<ReactionTarget>) => {
    setState((prev) => (prev.phase === 'ready' ? { phase: 'ready', post: { ...prev.post, ...patch } } : prev))
  }, [])
  const { toggle } = useReactions(token, updatePost, setToast)

  const handleRetry = () => {
    setState({ phase: 'loading' })
    setReloadKey((key) => key + 1)
  }

  return (
    <main className="post-detail-page">
      <header className="post-detail-header">
        <button type="button" className="post-detail-back" aria-label="戻る" onClick={() => goBack(navigate, location, '/')}>
          ‹
        </button>
        <h1 className="post-detail-heading">投稿</h1>
      </header>

      {state.phase === 'loading' && (
        <p className="post-detail-status" role="status">
          読み込み中...
        </p>
      )}
      {state.phase === 'notFound' && (
        <div className="post-detail-status" role="alert">
          <p className="post-detail-status-title">投稿が見つかりません</p>
          <p>削除されたか、URL が正しくない可能性があります。</p>
          <Link to="/" className="post-detail-action">
            ホームへ
          </Link>
        </div>
      )}
      {state.phase === 'error' && (
        <div className="post-detail-status" role="alert">
          <p>{state.message}</p>
          <button type="button" className="post-detail-action" onClick={handleRetry}>
            もう一度読み込む
          </button>
        </div>
      )}
      {state.phase === 'ready' && (
        <PostDetailContent
          post={state.post}
          onToggleLike={() => toggle(state.post, 'like')}
          onToggleFavorite={() => toggle(state.post, 'favorite')}
        />
      )}

      {toast && (
        <div className="post-detail-toast" role="alert">
          {toast}
        </div>
      )}
    </main>
  )
}

function PostDetailContent({
  post,
  onToggleLike,
  onToggleFavorite,
}: {
  post: PostResponse
  onToggleLike: () => void
  onToggleFavorite: () => void
}) {
  const { author } = post
  const height = formatHeight(author.heightCm)
  return (
    <article className="post-detail-body">
      {/* 写真の切り替えは投稿の確認画面と共通 */}
      <PostPhotoViewer urls={post.imageUrls} />

      <div className="post-detail-author-row">
        {/* 押すと投稿者のプロフィールへ（自分の投稿なら /users/{id} から /profile に切り替わる） */}
        <Link to={`/users/${author.id}`} className="post-detail-author">
          {author.profileImageUrl ? (
            <img className="post-detail-avatar" src={author.profileImageUrl} alt="" />
          ) : (
            <span className="post-detail-avatar post-detail-avatar-empty" aria-hidden="true">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4">
                <circle cx="12" cy="8.5" r="3.5" />
                <path d="M5 20c1.1-3.4 3.7-5 7-5s5.9 1.6 7 5" strokeLinecap="round" />
              </svg>
            </span>
          )}
          <span className="post-detail-author-text">
            <span className="post-detail-username">@{author.username}</span>
            {/* 身長が未設定なら、身長の部分そのものを出さない（ホームの一覧と同じ） */}
            {height !== null && <span className="post-detail-height">{height}</span>}
          </span>
        </Link>
        <div className="post-detail-reactions">
          <button
            type="button"
            className={`post-detail-like${post.likedByMe ? ' post-detail-active' : ''}`}
            aria-pressed={post.likedByMe}
            aria-label={`いいね（${post.likeCount}件）`}
            onClick={onToggleLike}
          >
            <HeartIcon filled={post.likedByMe} />
            <span aria-hidden="true">{post.likeCount}</span>
          </button>
          <button
            type="button"
            className={`post-detail-favorite${post.favoritedByMe ? ' post-detail-active' : ''}`}
            aria-pressed={post.favoritedByMe}
            aria-label="お気に入り"
            onClick={onToggleFavorite}
          >
            <StarIcon filled={post.favoritedByMe} />
          </button>
        </div>
      </div>

      <h2 className="post-detail-title">{post.title}</h2>

      <dl className="post-detail-list">
        <DetailRow label="ファッション">{post.fashionCategory.name}</DetailRow>
        <DetailRow label="タグ">
          {/* タグは ID 順（付けた順は保存していないため。docs/post.md） */}
          <ul className="post-detail-tags">
            {post.tags.map((tag) => (
              <li key={tag.id} className="post-detail-tag">
                {tag.name}
              </li>
            ))}
          </ul>
        </DetailRow>
        {/* 任意の項目は、未入力なら行ごと出さない */}
        {post.wornItems && (
          <DetailRow label="着用アイテム">
            <span className="post-detail-text">{post.wornItems}</span>
          </DetailRow>
        )}
        <DetailRow label="投稿説明">
          <span className="post-detail-text">{post.description}</span>
        </DetailRow>
        {post.referenceUrl && (
          <DetailRow label="参考情報">
            <ReferenceLink url={post.referenceUrl} />
          </DetailRow>
        )}
        <DetailRow label="投稿日">
          <time dateTime={post.createdAt}>{formatDate(post.createdAt)}</time>
        </DetailRow>
      </dl>
    </article>
  )
}

function DetailRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="post-detail-row">
      <dt className="post-detail-label">{label}</dt>
      <dd className="post-detail-value">{children}</dd>
    </div>
  )
}

/**
 * 参考情報の URL。http・https のときだけリンクにする（javascript: などの URL を開かせないため。サーバーでも確認済み）。
 * 外部のサイトのため別タブで開き、開いた先からこの画面を操作できないようにする
 */
function ReferenceLink({ url }: { url: string }) {
  if (!/^https?:\/\//i.test(url)) {
    return <span className="post-detail-text">{url}</span>
  }
  return (
    <a className="post-detail-link" href={url} target="_blank" rel="noopener noreferrer nofollow">
      {url}
    </a>
  )
}

/** 投稿日を「2026年10月8日」の形にする（バックエンドの日時はタイムゾーンなしの日本時間） */
function formatDate(dateTime: string): string {
  const date = new Date(dateTime)
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

export default PostDetailPage
