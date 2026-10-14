import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { fetchBlocks, unblockUser, type BlockedUser } from '../../api/blocks'
import { useAuth } from '../../auth/authContext'
import { BlockConfirmDialog } from '../../components/block/BlockConfirmDialog'
import { unblockConfirmText } from '../../components/block/blockText'
import { useLoadMoreOnScroll, usePagedList } from '../../hooks/usePagedList'
import { goBack } from './goBack'
import './settings.css'

/**
 * ブロック中のアカウント（/settings/blocks。docs/profile.md「ブロック」）。詳細設定から開く。
 * 自分がブロックした人を、ブロックした新しい順に出し、1人ずつ解除できる（解除は画面の中で確かめる）
 */
function BlockListPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  const { state, loadMore, retry, removeItem } = usePagedList<BlockedUser & { id: number }>(
    (page, authToken, signal) =>
      fetchBlocks(page, authToken, signal).then((result) => ({
        items: result.items.map((item) => ({ ...item, id: item.userId })),
        hasNext: result.hasNext,
      })),
    token,
  )
  const sentinelRef = useLoadMoreOnScroll(loadMore)
  const [target, setTarget] = useState<BlockedUser | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleUnblock = async () => {
    if (!token || !target || busy) return
    setBusy(true)
    setError(null)
    try {
      await unblockUser(target.userId, token)
      removeItem(target.userId)
      setTarget(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'ブロックを解除できませんでした。時間をおいて再度お試しください。')
    } finally {
      setBusy(false)
    }
  }

  const isEmpty = state.items.length === 0 && !state.hasNext && state.status === 'idle'
  const text = target ? unblockConfirmText(target.username) : null

  return (
    <main className="settings-page">
      <header className="settings-header">
        <button
          type="button"
          className="settings-back"
          aria-label="戻る"
          onClick={() => goBack(navigate, location, '/settings')}
        >
          ‹
        </button>
        <h1 className="settings-title">ブロック中のアカウント</h1>
      </header>

      {isEmpty ? (
        <p className="blocks-empty">ブロック中のアカウントはありません</p>
      ) : (
        <ul className="blocks-list">
          {state.items.map((user) => (
            <li key={user.userId} className="blocks-item">
              {user.profileImageUrl ? (
                <img className="blocks-avatar" src={user.profileImageUrl} alt="" />
              ) : (
                <span className="blocks-avatar blocks-avatar-empty" aria-hidden="true" />
              )}
              <span className="blocks-names">
                {/* 自分がブロックした相手のプロフィールは開ける（「ブロック中のユーザーです」と解除のボタンが出る） */}
                <Link to={`/users/${user.userId}`} className="blocks-username">
                  @{user.username}
                </Link>
                <span className="blocks-date">{formatDate(user.blockedAt)} にブロック</span>
              </span>
              <button
                type="button"
                className="blocks-unblock"
                onClick={() => {
                  setError(null)
                  setTarget(user)
                }}
              >
                解除
              </button>
            </li>
          ))}
        </ul>
      )}

      {state.status === 'loading' && (
        <p className="blocks-status" role="status">
          読み込み中...
        </p>
      )}
      {state.status === 'error' && (
        <div className="blocks-status" role="alert">
          <p>{state.errorMessage}</p>
          <button type="button" className="blocks-unblock" onClick={retry}>
            もう一度読み込む
          </button>
        </div>
      )}
      {state.hasNext && <div ref={sentinelRef} className="blocks-sentinel" aria-hidden="true" />}

      {target && text && (
        <BlockConfirmDialog
          title={text.title}
          body={text.body}
          confirmLabel="解除する"
          busyLabel="解除しています..."
          busy={busy}
          error={error}
          onConfirm={handleUnblock}
          onCancel={() => setTarget(null)}
        />
      )}
    </main>
  )
}

/** ブロックした日（"2026-10-12T10:00:00" を「2026/10/12」の形にする。サーバーの日付をそのまま出す） */
function formatDate(value: string): string {
  const m = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
  return m === null ? value : `${m[1]}/${m[2]}/${m[3]}`
}

export default BlockListPage
