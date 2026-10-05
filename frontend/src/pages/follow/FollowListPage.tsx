import { useEffect, useId, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client'
import { fetchConsultationStatuses, type ConsultationStatus } from '../../api/consultations'
import { fetchFollowList, setFollow, type FollowListItem, type FollowSort } from '../../api/follows'
import { fetchMasters, type EnumOption } from '../../api/masters'
import { fetchMyProfile } from '../../api/profile'
import { useAuth } from '../../auth/authContext'
import { ConsultButton } from '../../components/consult/ConsultButton'
import { consultNote } from '../../components/consult/consultLabels'
import { useLoadMoreOnScroll, usePagedList } from '../../hooks/usePagedList'
import { timeAgo } from '../../utils/timeAgo'
import { goBack } from '../settings/goBack'
import './follow.css'

type Kind = 'followings' | 'followers'

const TITLES: Record<Kind, string> = { followings: 'フォロー中', followers: 'フォロワー' }

/** 検索欄の入力が止まってから検索するまでの時間（1文字ごとに API を呼ばないため） */
const SEARCH_DELAY_MS = 300

/**
 * フォロー中一覧（/users/{id}/followings）・フォロワー一覧（/users/{id}/followers）。
 * docs/profile.md「フォロー中一覧」、design/Following List.png。フォロワー一覧の画面仕様は未確定のため、フォロー中一覧と同じにしている
 */
export function FollowListPage({ kind }: { kind: Kind }) {
  const { userId } = useParams()
  const id = userId !== undefined && /^\d+$/.test(userId) ? Number(userId) : null
  // 別のユーザー・別の一覧に移ったら、検索語・並び順・読み込んだ内容を作り直す
  return <FollowListView key={`${kind}-${id}`} kind={kind} userId={id} />
}

function FollowListView({ kind, userId }: { kind: Kind; userId: number | null }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  const searchId = useId()
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<FollowSort>('newest')
  const [totalCount, setTotalCount] = useState<number | null>(null)
  const [myId, setMyId] = useState<number | null>(null)
  const [genders, setGenders] = useState<EnumOption[]>([])

  // 入力が止まってから検索する
  useEffect(() => {
    const timer = setTimeout(() => setQuery(input.trim()), SEARCH_DELAY_MS)
    return () => clearTimeout(timer)
  }, [input])

  // 自分の行にはボタンを出さないため、自分の ID を調べる。性別の表示名は選択肢の一覧から引く
  useEffect(() => {
    if (!token) return
    let cancelled = false
    Promise.all([fetchMyProfile(token), fetchMasters(token)])
      .then(([profile, masters]) => {
        if (cancelled) return
        setMyId(profile.id)
        setGenders(masters.genders)
      })
      .catch(() => {
        // 読めなくても一覧は表示できる（ボタン・性別の表示が省かれるだけ）
      })
    return () => {
      cancelled = true
    }
  }, [token])

  if (userId === null) {
    return (
      <main className="follow-page">
        <Header title={TITLES[kind]} count={null} onBack={() => goBack(navigate, location, '/profile')} />
        <p className="follow-message" role="alert">
          ユーザーが見つかりません。
        </p>
      </main>
    )
  }

  // 自分のフォロー中一覧で、フォロー・解除すると、画面上部の件数も変わる
  const isMyFollowings = kind === 'followings' && myId === userId

  return (
    <main className="follow-page">
      <Header title={TITLES[kind]} count={totalCount} onBack={() => goBack(navigate, location, `/users/${userId}`)} />

      <div className="follow-controls">
        <label className="follow-search" htmlFor={searchId}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
            <circle cx="11" cy="11" r="6.5" />
            <path d="m20 20-4.2-4.2" strokeLinecap="round" />
          </svg>
          <input
            id={searchId}
            type="search"
            placeholder="ユーザー名で検索"
            aria-label="ユーザー名で検索"
            maxLength={50}
            value={input}
            onChange={(e) => setInput(e.target.value)}
          />
        </label>
        <label className="follow-sort">
          <span className="follow-visually-hidden">並び順</span>
          <select value={sort} onChange={(e) => setSort(e.target.value as FollowSort)}>
            <option value="newest">フォローの新しい順</option>
            <option value="oldest">フォローの古い順</option>
          </select>
        </label>
      </div>

      {/* 検索語・並び順を変えたら一覧を作り直す */}
      <FollowList
        key={`${query}-${sort}`}
        kind={kind}
        userId={userId}
        query={query}
        sort={sort}
        myId={myId}
        genders={genders}
        onTotalCount={setTotalCount}
        onFollowChange={(following) => {
          if (isMyFollowings) setTotalCount((prev) => (prev === null ? prev : prev + (following ? 1 : -1)))
        }}
      />
    </main>
  )
}

function Header({ title, count, onBack }: { title: string; count: number | null; onBack: () => void }) {
  return (
    <header className="follow-header">
      <button type="button" className="follow-back" aria-label="戻る" onClick={onBack}>
        ‹
      </button>
      <h1 className="follow-title">
        {title}
        {count !== null && <span className="follow-count">{count.toLocaleString('ja-JP')}人</span>}
      </h1>
    </header>
  )
}

function FollowList({
  kind,
  userId,
  query,
  sort,
  myId,
  genders,
  onTotalCount,
  onFollowChange,
}: {
  kind: Kind
  userId: number
  query: string
  sort: FollowSort
  myId: number | null
  genders: EnumOption[]
  onTotalCount: (count: number) => void
  onFollowChange: (following: boolean) => void
}) {
  const { token } = useAuth()
  // 「相談する」ボタンの状態（ユーザー ID → 状態）。1ページ読むごとに、そのページの人数分をまとめて読む
  const [consultStatuses, setConsultStatuses] = useState<Map<number, ConsultationStatus>>(new Map())
  const loadConsultStatuses = (userIds: number[], authToken: string, signal?: AbortSignal) => {
    if (userIds.length === 0) return
    fetchConsultationStatuses(userIds, authToken, signal)
      .then((statuses) => setConsultStatuses((prev) => new Map([...prev, ...statuses])))
      .catch(() => {
        // 読めなかった人のボタンは押せないままにする（フォローなど他の操作は使える）
      })
  }
  const { state, loadMore, retry, updateItem } = usePagedList<FollowListItem>(
    (page, authToken, signal) =>
      fetchFollowList(kind, userId, query, sort, page, authToken, signal).then((result) => {
        onTotalCount(result.totalCount)
        // 一覧は先に表示し、ボタンの状態は後から反映する
        loadConsultStatuses(result.users.map((user) => user.id), authToken, signal)
        return { items: result.users, hasNext: result.hasNext }
      }),
    token,
  )
  const sentinelRef = useLoadMoreOnScroll(loadMore)
  const [pending, setPending] = useState<Set<number>>(new Set())
  const [error, setError] = useState<string | null>(null)

  // 通信中は同じ人のボタンを押せないようにし、結果はサーバーの応答で確定する
  const handleFollow = async (user: FollowListItem) => {
    if (!token || pending.has(user.id)) return
    setPending((prev) => new Set(prev).add(user.id))
    setError(null)
    try {
      const result = await setFollow(user.id, !user.followingByMe, token)
      updateItem(user.id, { followingByMe: result.following })
      if (result.following !== user.followingByMe) onFollowChange(result.following)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '操作できませんでした。時間をおいて再度お試しください。')
    } finally {
      setPending((prev) => {
        const next = new Set(prev)
        next.delete(user.id)
        return next
      })
    }
  }

  const users = state.items
  const isEmpty = users.length === 0 && !state.hasNext && state.status === 'idle'
  const emptyText =
    query !== ''
      ? `「${query}」に一致するユーザーはいません`
      : kind === 'followings'
        ? 'フォロー中のユーザーはいません'
        : 'フォロワーはいません'

  return (
    <>
      {error && (
        <p className="follow-error" role="alert">
          {error}
        </p>
      )}
      {isEmpty ? (
        <p className="follow-message">{emptyText}</p>
      ) : (
        <ul className="follow-list">
          {users.map((user) => (
            <li key={user.id}>
              <UserRow
                user={user}
                isMe={user.id === myId}
                genders={genders}
                pending={pending.has(user.id)}
                consultStatus={consultStatuses.get(user.id) ?? null}
                onFollow={() => handleFollow(user)}
                onConsultStatusChanged={() => {
                  if (token) loadConsultStatuses([user.id], token)
                }}
              />
            </li>
          ))}
        </ul>
      )}

      {state.status === 'loading' && (
        <p className="follow-message" role="status">
          読み込み中...
        </p>
      )}
      {state.status === 'error' && (
        <div className="follow-message" role="alert">
          <p>{state.errorMessage}</p>
          <button type="button" className="follow-retry" onClick={retry}>
            もう一度読み込む
          </button>
        </div>
      )}
      {/* 次のページがある間だけ置く、無限スクロールの目印 */}
      {state.hasNext && <div ref={sentinelRef} className="follow-sentinel" aria-hidden="true" />}
    </>
  )
}

function UserRow({
  user,
  isMe,
  genders,
  pending,
  consultStatus,
  onFollow,
  onConsultStatusChanged,
}: {
  user: FollowListItem
  isMe: boolean
  genders: EnumOption[]
  pending: boolean
  consultStatus: ConsultationStatus | null
  onFollow: () => void
  onConsultStatusChanged: () => void
}) {
  const note = isMe ? null : consultNote(consultStatus)
  // 設定されている項目だけを「・」でつなぐ（プロフィール画面と同じ）
  const details = [
    user.heightCm !== null ? `${user.heightCm}cm` : null,
    genders.find((option) => option.code === user.gender)?.label,
  ].filter((value): value is string => !!value)

  return (
    <article className="follow-card">
      <Link to={`/users/${user.id}`} className="follow-user">
        {user.profileImageUrl ? (
          <img className="follow-avatar" src={user.profileImageUrl} alt="" />
        ) : (
          <span className="follow-avatar follow-avatar-empty" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4">
              <circle cx="12" cy="8.5" r="3.5" />
              <path d="M5 20c1.1-3.4 3.7-5 7-5s5.9 1.6 7 5" strokeLinecap="round" />
            </svg>
          </span>
        )}
        <span className="follow-text">
          <span className="follow-username">@{user.username}</span>
          {details.length > 0 && <span className="follow-details">{details.join(' ・ ')}</span>}
          <span className="follow-since">{timeAgo(user.followedAt)}からフォロー</span>
          {/* 上限に達している相手には「現在、新しい相談を受け付けていません」と表示する（docs/dm.md） */}
          {note && <span className="follow-consult-note">{note}</span>}
        </span>
      </Link>

      {!isMe && (
        <div className="follow-buttons">
          <ConsultButton
            userId={user.id}
            username={user.username}
            status={consultStatus}
            label="相談する"
            className="follow-consult"
            onStatusChanged={onConsultStatusChanged}
          />
          <button
            type="button"
            className={`follow-toggle${user.followingByMe ? '' : ' follow-toggle-off'}`}
            aria-pressed={user.followingByMe}
            aria-label={`@${user.username} を${user.followingByMe ? 'フォロー解除' : 'フォロー'}`}
            disabled={pending}
            onClick={onFollow}
          >
            {user.followingByMe ? 'フォロー解除' : 'フォロー'}
          </button>
        </div>
      )}
    </article>
  )
}
