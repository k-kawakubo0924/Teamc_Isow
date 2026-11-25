import { useEffect, useId, useState, type FormEvent } from 'react'
import {
  fetchModerationPost,
  fetchModerationUser,
  findModerationUser,
  listHiddenPosts,
  listSuspendedUsers,
  type HiddenPostList,
  type ModerationPostState,
  type ModerationUserState,
  type SuspendedUserList,
} from '../../api/admin'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/authContext'
import { formatAdminDateTime } from './adminFormat'
import { PostModerationPanel, UserModerationPanel } from './ModerationPanels'

type Selected = { kind: 'post'; state: ModerationPostState } | { kind: 'user'; state: ModerationUserState } | null

/**
 * 措置（docs/admin.md「6. 措置」）。ユーザー名・投稿 ID で探して、投稿の非公開化・利用停止を行う。
 * 通報から措置する場合は、通報の詳細の「措置」から行う（措置に通報が結びつく）。
 * 下に、いま非公開にしている投稿と、いま利用停止中のユーザーを出す（期限を過ぎた利用停止は出さない）
 */
function AdminModerationPage() {
  const { token } = useAuth()
  const usernameId = useId()
  const postIdId = useId()
  const [username, setUsername] = useState('')
  const [postId, setPostId] = useState('')
  const [selected, setSelected] = useState<Selected>(null)
  const [searchError, setSearchError] = useState<string | null>(null)
  const [hiddenPosts, setHiddenPosts] = useState<HiddenPostList | null>(null)
  const [suspendedUsers, setSuspendedUsers] = useState<SuspendedUserList | null>(null)
  const [listError, setListError] = useState<string | null>(null)
  /** 措置をしたら増やし、一覧を読み直す */
  const [listVersion, setListVersion] = useState(0)

  useEffect(() => {
    if (!token) return
    let cancelled = false
    Promise.all([listHiddenPosts(0, token), listSuspendedUsers(0, token)])
      .then(([posts, users]) => {
        if (cancelled) return
        setHiddenPosts(posts)
        setSuspendedUsers(users)
        setListError(null)
      })
      .catch((err: unknown) => {
        if (!cancelled) setListError(err instanceof Error ? err.message : '一覧を読み込めませんでした。')
      })
    return () => {
      cancelled = true
    }
  }, [token, listVersion])
  const reloadLists = () => setListVersion((v) => v + 1)

  const load = async (loader: () => Promise<Selected>) => {
    setSearchError(null)
    try {
      setSelected(await loader())
    } catch (err) {
      setSelected(null)
      setSearchError(err instanceof ApiError && err.status === 404 ? '見つかりませんでした。' : errorText(err))
    }
  }

  const searchUser = (e: FormEvent) => {
    e.preventDefault()
    const name = username.trim()
    if (!token || name === '') return
    void load(async () => ({ kind: 'user', state: await findModerationUser(name, token) }))
  }

  const searchPost = (e: FormEvent) => {
    e.preventDefault()
    if (!token) return
    const value = postId.trim()
    if (!/^\d+$/.test(value)) {
      setSelected(null)
      setSearchError('投稿 ID は数字で入力してください。')
      return
    }
    void load(async () => ({ kind: 'post', state: await fetchModerationPost(Number(value), token) }))
  }

  const openUser = (id: number) => {
    if (!token) return
    void load(async () => ({ kind: 'user', state: await fetchModerationUser(id, token) }))
  }

  const openPost = (id: number) => {
    if (!token) return
    void load(async () => ({ kind: 'post', state: await fetchModerationPost(id, token) }))
  }

  return (
    <>
      <h1 className="admin-page-title">措置</h1>
      <p className="admin-moderation-notice" role="note">
        投稿の非公開化と、ユーザーの利用停止を行います。通報から措置する場合は、通報の詳細の「措置」から行ってください（措置に通報が結びつきます）。
        投稿の削除はまだできません。すべての措置は操作ログに残ります（メモはログに書きません）。
      </p>

      <div className="admin-moderation-search">
        <form className="admin-moderation-search-form" onSubmit={searchUser}>
          <label htmlFor={usernameId} className="admin-moderation-label">
            ユーザー名
          </label>
          <input
            id={usernameId}
            className="admin-moderation-input"
            value={username}
            placeholder="@ なしでも可"
            onChange={(e) => setUsername(e.target.value)}
          />
          <button type="submit" className="admin-moderation-button">
            ユーザーを探す
          </button>
        </form>
        <form className="admin-moderation-search-form" onSubmit={searchPost}>
          <label htmlFor={postIdId} className="admin-moderation-label">
            投稿 ID
          </label>
          <input
            id={postIdId}
            className="admin-moderation-input"
            inputMode="numeric"
            value={postId}
            onChange={(e) => setPostId(e.target.value)}
          />
          <button type="submit" className="admin-moderation-button">
            投稿を探す
          </button>
        </form>
      </div>
      {searchError && (
        <p className="admin-moderation-error" role="alert">
          {searchError}
        </p>
      )}

      {selected?.kind === 'user' && (
        <UserModerationPanel
          key={`user-${selected.state.userId}`}
          state={selected.state}
          onChange={(state) => {
            setSelected({ kind: 'user', state })
            reloadLists()
          }}
        />
      )}
      {selected?.kind === 'post' && (
        <PostModerationPanel
          key={`post-${selected.state.postId}`}
          state={selected.state}
          onChange={(state) => {
            setSelected({ kind: 'post', state })
            reloadLists()
          }}
        />
      )}

      {listError && <p className="admin-moderation-error">{listError}</p>}

      <h2 className="admin-moderation-heading">非公開にしている投稿</h2>
      {hiddenPosts !== null &&
        (hiddenPosts.items.length === 0 ? (
          <p className="admin-moderation-hint">ありません。</p>
        ) : (
          <table className="admin-logs-table">
            <thead>
              <tr>
                <th>題名</th>
                <th>投稿者</th>
                <th>理由</th>
                <th>非公開にした日時</th>
              </tr>
            </thead>
            <tbody>
              {hiddenPosts.items.map((item) => (
                <tr key={item.postId}>
                  <td>
                    <button type="button" className="admin-moderation-link" onClick={() => openPost(item.postId)}>
                      {item.title}
                    </button>
                  </td>
                  <td>@{item.authorUsername}</td>
                  <td>{item.hiddenReasonLabel}</td>
                  <td>{formatAdminDateTime(item.hiddenAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ))}

      <h2 className="admin-moderation-heading">利用停止中のユーザー</h2>
      {suspendedUsers !== null &&
        (suspendedUsers.items.length === 0 ? (
          <p className="admin-moderation-hint">ありません。</p>
        ) : (
          <table className="admin-logs-table">
            <thead>
              <tr>
                <th>ユーザー</th>
                <th>理由</th>
                <th>利用停止にした日時</th>
                <th>期限</th>
              </tr>
            </thead>
            <tbody>
              {suspendedUsers.items.map((item) => (
                <tr key={item.userId}>
                  <td>
                    <button type="button" className="admin-moderation-link" onClick={() => openUser(item.userId)}>
                      @{item.username}
                    </button>
                  </td>
                  <td>{item.suspensionReasonLabel}</td>
                  <td>{formatAdminDateTime(item.suspendedAt)}</td>
                  <td>{item.suspendedUntil === null ? '無期限' : formatAdminDateTime(item.suspendedUntil)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        ))}
    </>
  )
}

function errorText(err: unknown): string {
  return err instanceof Error ? err.message : '読み込めませんでした。'
}

export default AdminModerationPage
