import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client'
import { fetchPost, type PostResponse } from '../../api/posts'
import { useAuth } from '../../auth/authContext'
import PostPage from './PostPage'
import './post.css'

type State =
  | { phase: 'loading' }
  | { phase: 'ready'; post: PostResponse }
  | { phase: 'notFound' }
  | { phase: 'forbidden' }
  | { phase: 'error'; message: string }

/**
 * 投稿の編集（/posts/{id}/edit。docs/post.md「投稿の編集」）。
 * 投稿を読み込み、自分の投稿なら投稿作成と同じ画面（PostPage）を編集の形で出す。
 * 他人の投稿・存在しない投稿では入力欄を出さない（表示を分けるだけで、編集できるかはサーバーが確かめる）
 */
function PostEditPage() {
  const { postId } = useParams()
  const id = postId !== undefined && /^\d+$/.test(postId) ? Number(postId) : null
  // 別の投稿の編集に移ったら、読み込んだ内容・入力中の内容を作り直す
  return <PostEditView key={id ?? 'invalid'} postId={id} />
}

function PostEditView({ postId }: { postId: number | null }) {
  const navigate = useNavigate()
  const { token } = useAuth()
  // ID が数字でなければ、API を呼ばずに「投稿が見つかりません」を出す
  const [state, setState] = useState<State>(postId === null ? { phase: 'notFound' } : { phase: 'loading' })
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (postId === null || !token) return
    const controller = new AbortController()
    fetchPost(postId, token, controller.signal)
      .then((post) => setState(post.ownedByMe ? { phase: 'ready', post } : { phase: 'forbidden' }))
      .catch((err: unknown) => {
        if (controller.signal.aborted) return
        if (err instanceof ApiError && err.status === 404) {
          setState({ phase: 'notFound' })
        } else {
          setState({ phase: 'error', message: err instanceof Error ? err.message : String(err) })
        }
      })
    return () => controller.abort()
  }, [postId, token, reloadKey])

  if (state.phase === 'ready') {
    return <PostPage editing={state.post} />
  }

  return (
    <main className="post-page">
      <header className="post-header">
        <button type="button" className="post-header-back" aria-label="前の画面に戻る" onClick={() => navigate(-1)}>
          ‹
        </button>
        <h1 className="post-title">投稿の編集</h1>
      </header>
      <div className="post-body">
        {state.phase === 'loading' && (
          <p className="post-hint" role="status">
            読み込み中...
          </p>
        )}
        {state.phase === 'notFound' && (
          <div className="post-edit-status" role="alert">
            <p className="post-edit-status-title">投稿が見つかりません</p>
            <p>削除されたか、URL が正しくない可能性があります。</p>
            <Link to="/" className="post-button post-button-secondary">
              ホームへ
            </Link>
          </div>
        )}
        {state.phase === 'forbidden' && (
          <div className="post-edit-status" role="alert">
            <p className="post-edit-status-title">この投稿は編集できません</p>
            <p>編集できるのは、自分の投稿だけです。</p>
            {postId !== null && (
              <Link to={`/posts/${postId}`} className="post-button post-button-secondary">
                投稿を見る
              </Link>
            )}
          </div>
        )}
        {state.phase === 'error' && (
          <div className="post-edit-status" role="alert">
            <p>{state.message}</p>
            <button
              type="button"
              className="post-button post-button-secondary"
              onClick={() => {
                setState({ phase: 'loading' })
                setReloadKey((key) => key + 1)
              }}
            >
              もう一度読み込む
            </button>
          </div>
        )}
      </div>
    </main>
  )
}

export default PostEditPage
