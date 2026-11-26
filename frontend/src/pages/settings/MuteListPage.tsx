import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { fetchMutes, unmuteUser, type MutedUser } from '../../api/mutes'
import { useAuth } from '../../auth/authContext'
import { MuteNotice } from '../../components/mute/MuteNotice'
import { muteMenuLabel, muteNoticeText } from '../../components/mute/muteText'
import { useLoadMoreOnScroll, usePagedList } from '../../hooks/usePagedList'
import { goBack } from './goBack'
import './settings.css'

/** 知らせを出しておく時間（ミリ秒） */
const NOTICE_MS = 5000

/**
 * 投稿を非表示にしている人（/settings/mutes。docs/profile.md「ミュート（投稿の非表示）」）。詳細設定から開く。
 * ブロック中のアカウント（BlockListPage）と同じ形。自分がミュートした人を、ミュートした新しい順に出し、1人ずつ解除できる。
 * 解除は確認を出さない（何も壊さず、いつでも戻せる軽い手段のため）。解除したら一覧から外し、したことを短く知らせる
 */
function MuteListPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  const { state, loadMore, retry, removeItem } = usePagedList<MutedUser & { id: number }>(
    (page, authToken, signal) =>
      fetchMutes(page, authToken, signal).then((result) => ({
        items: result.items.map((item) => ({ ...item, id: item.userId })),
        hasNext: result.hasNext,
      })),
    token,
  )
  const sentinelRef = useLoadMoreOnScroll(loadMore)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  useEffect(() => {
    if (notice === null) return
    const timer = setTimeout(() => setNotice(null), NOTICE_MS)
    return () => clearTimeout(timer)
  }, [notice])

  const handleUnmute = async (user: MutedUser) => {
    if (!token || busyId !== null) return
    setBusyId(user.userId)
    setError(null)
    setNotice(null)
    try {
      await unmuteUser(user.userId, token)
      removeItem(user.userId)
      setNotice(muteNoticeText(user.username, false))
    } catch (err) {
      setError(err instanceof Error ? err.message : '変更できませんでした。時間をおいて再度お試しください。')
    } finally {
      setBusyId(null)
    }
  }

  const isEmpty = state.items.length === 0 && !state.hasNext && state.status === 'idle'

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
        <h1 className="settings-title">投稿を非表示にしている人</h1>
      </header>

      <p className="mutes-lead">ここにいる人の投稿は、ホームや検索に出ません。プロフィールからは、これまでどおり見られます。</p>

      {isEmpty ? (
        <p className="blocks-empty">投稿を非表示にしている人はいません</p>
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
                <Link to={`/users/${user.userId}`} className="blocks-username">
                  @{user.username}
                </Link>
                <span className="blocks-date">{formatDate(user.mutedAt)} から非表示にしています</span>
              </span>
              <button
                type="button"
                className="blocks-unblock"
                disabled={busyId !== null}
                onClick={() => void handleUnmute(user)}
              >
                {muteMenuLabel(true)}
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

      <MuteNotice notice={notice} error={error} />
    </main>
  )
}

/** ミュートした日（"2026-11-20T10:00:00" を「2026/11/20」の形にする。サーバーの日付をそのまま出す） */
function formatDate(value: string): string {
  const m = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
  return m === null ? value : `${m[1]}/${m[2]}/${m[3]}`
}

export default MuteListPage
