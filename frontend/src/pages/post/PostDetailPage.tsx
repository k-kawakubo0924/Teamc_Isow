import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { blockUser } from '../../api/blocks'
import { ApiError } from '../../api/client'
import { deletePost, fetchPost, type PostResponse } from '../../api/posts'
import { useAuth } from '../../auth/authContext'
import { BlockConfirmDialog } from '../../components/block/BlockConfirmDialog'
import { blockConfirmText } from '../../components/block/blockText'
import { ReportDialog } from '../../components/report/ReportDialog'
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
 * 自分の投稿にだけ、見出しの右に編集・削除のメニューを出す（docs/post.md「投稿の編集」「投稿の削除」。できるかはサーバーが確かめる）
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

  // 自分の投稿のときだけ、編集・削除のメニューを出す（表示を分けるだけで、できるかはサーバーが確かめる）
  const ownPost = state.phase === 'ready' && state.post.ownedByMe ? state.post : null
  // 他人の投稿には、通報・ブロックのメニューを出す（docs/admin.md「通報」・docs/profile.md「ブロック」）
  const otherPost = state.phase === 'ready' && !state.post.ownedByMe ? state.post : null

  return (
    <main className="post-detail-page">
      <header className="post-detail-header">
        <button type="button" className="post-detail-back" aria-label="戻る" onClick={() => goBack(navigate, location, '/')}>
          ‹
        </button>
        <h1 className="post-detail-heading">投稿</h1>
        {ownPost && token && <PostMenu postId={ownPost.id} token={token} />}
        {otherPost && token && <OtherPostMenu post={otherPost} token={token} />}
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

/**
 * 自分の投稿の「…」メニュー（編集・削除）と、削除の確認（docs/post.md「投稿の編集」「投稿の削除」）。
 * 削除は元に戻せないため、ブラウザの確認ダイアログではなく画面の中で確かめる。
 * 削除できたら、削除した投稿に「戻る」で戻らないよう、履歴を置き換えて自分のプロフィールへ移る
 */
function PostMenu({ postId, token }: { postId: number; token: string }) {
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const openConfirm = () => {
    setMenuOpen(false)
    setError(null)
    setConfirming(true)
  }

  const handleDelete = async () => {
    setDeleting(true)
    setError(null)
    try {
      await deletePost(postId, token)
    } catch (err: unknown) {
      // すでに削除されていた（別の画面で削除したなど）場合は、削除できたものとして扱う
      if (!(err instanceof ApiError && err.status === 404)) {
        setError(err instanceof Error ? err.message : String(err))
        setDeleting(false)
        return
      }
    }
    navigate('/profile', { replace: true })
  }

  return (
    <>
      <div className="post-detail-menu">
        <button
          type="button"
          className="post-detail-menu-button"
          aria-label="投稿のメニュー"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          …
        </button>
        {menuOpen && (
          <div className="post-detail-menu-list" role="menu">
            {/* 編集は文字の情報だけ（写真は変えられない。docs/post.md「投稿の編集」） */}
            <button
              type="button"
              role="menuitem"
              className="post-detail-menu-edit"
              onClick={() => navigate(`/posts/${postId}/edit`)}
            >
              編集
            </button>
            <button type="button" role="menuitem" className="post-detail-menu-delete" onClick={openConfirm}>
              削除
            </button>
          </div>
        )}
      </div>

      {confirming && (
        <div className="post-detail-confirm-backdrop">
          <div
            className="post-detail-confirm"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="post-detail-confirm-title"
            aria-describedby="post-detail-confirm-text"
          >
            <p id="post-detail-confirm-title" className="post-detail-confirm-title">
              この投稿を削除しますか？
            </p>
            <p id="post-detail-confirm-text" className="post-detail-confirm-text">
              写真・いいね・お気に入りも削除され、元に戻せません。
            </p>
            {error && (
              <p className="post-detail-confirm-error" role="alert">
                {error}
              </p>
            )}
            <div className="post-detail-confirm-buttons">
              <button
                type="button"
                className="post-detail-confirm-cancel"
                disabled={deleting}
                autoFocus
                onClick={() => setConfirming(false)}
              >
                キャンセル
              </button>
              <button type="button" className="post-detail-confirm-ok" disabled={deleting} onClick={handleDelete}>
                {deleting ? '削除しています...' : '削除する'}
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}

/**
 * 他人の投稿の「…」メニュー（通報・ブロック）。ブロックしたら、その投稿は見えなくなるため、履歴を置き換えてホームへ移る
 */
function OtherPostMenu({ post, token }: { post: PostResponse; token: string }) {
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [dialog, setDialog] = useState<'report' | 'block' | null>(null)
  const [blocking, setBlocking] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleBlocked = () => navigate('/', { replace: true })

  const handleBlock = async () => {
    setBlocking(true)
    setError(null)
    try {
      await blockUser(post.author.id, token)
      handleBlocked()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'ブロックできませんでした。時間をおいて再度お試しください。')
      setBlocking(false)
    }
  }

  const open = (next: 'report' | 'block') => {
    setMenuOpen(false)
    setError(null)
    setDialog(next)
  }

  const text = blockConfirmText(post.author.username)
  return (
    <>
      <div className="post-detail-menu">
        <button
          type="button"
          className="post-detail-menu-button"
          aria-label="投稿のメニュー"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((value) => !value)}
        >
          …
        </button>
        {menuOpen && (
          <div className="post-detail-menu-list" role="menu">
            <button type="button" role="menuitem" className="post-detail-menu-delete" onClick={() => open('report')}>
              通報
            </button>
            <button type="button" role="menuitem" className="post-detail-menu-delete" onClick={() => open('block')}>
              ブロック
            </button>
          </div>
        )}
      </div>
      {dialog === 'report' && (
        <ReportDialog
          targetType="POST"
          targetId={post.id}
          owner={{ id: post.author.id, username: post.author.username }}
          blockable
          onClose={() => setDialog(null)}
          onBlocked={handleBlocked}
        />
      )}
      {dialog === 'block' && (
        <BlockConfirmDialog
          title={text.title}
          body={text.body}
          confirmLabel="ブロックする"
          busyLabel="ブロックしています..."
          busy={blocking}
          error={error}
          onConfirm={handleBlock}
          onCancel={() => setDialog(null)}
        />
      )}
    </>
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
          {/* 投稿者が編集した投稿には印を出す（いつ・何を変えたかの履歴は持たない） */}
          {post.editedAt && <span className="post-detail-edited">編集済み</span>}
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
