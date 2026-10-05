import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client'
import { fetchConsultationStatus, type ConsultationStatus } from '../../api/consultations'
import { setFollow } from '../../api/follows'
import { fetchMasters, type MastersResponse } from '../../api/masters'
import {
  fetchFollowingPosts,
  fetchMyFavorites,
  fetchMyProfile,
  fetchProfile,
  fetchUserPosts,
  newShuffleSeed,
  type ProfileResponse,
} from '../../api/profile'
import { useAuth } from '../../auth/authContext'
import { ConsultButton } from '../../components/consult/ConsultButton'
import { consultNote } from '../../components/consult/consultLabels'
import { PROFILE_COLUMNS, loadColumns, saveColumns, type Columns } from '../home/columnSetting'
import { ColumnsSwitcher, EmptyMessage, PostGrid, type FetchPostPage } from '../home/PostGrid'
import { goBack } from '../settings/goBack'
import './profile.css'

type MyTab = 'posts' | 'favorites'
type OtherTab = 'posts' | 'following'

const MY_TABS: { key: MyTab; label: string }[] = [
  { key: 'posts', label: '自分の投稿' },
  { key: 'favorites', label: 'お気に入り' },
]

const OTHER_TABS: { key: OtherTab; label: string }[] = [
  { key: 'posts', label: '投稿' },
  { key: 'following', label: 'フォロー中の投稿' },
]

/** 自分のプロフィール（/profile。docs/profile.md、design/myprofile.png） */
export function MyProfilePage() {
  return <ProfileView userId={null} />
}

/**
 * 相手のプロフィール（/users/{id}。design/otherprofile.png）。
 * 自分の ID を開いた場合は /profile に切り替える（ホームの投稿カードから自分の名前を押した場合など）
 */
export function UserProfilePage() {
  const { userId } = useParams()
  const id = userId !== undefined && /^\d+$/.test(userId) ? Number(userId) : null
  if (id === null) {
    return <ProfileNotFound />
  }
  // 別のユーザーに移ったら、読み込み・タブ・フォローの状態を作り直す
  return <ProfileView key={id} userId={id} />
}

/** userId が null なら自分のプロフィール */
function ProfileView({ userId }: { userId: number | null }) {
  const { token } = useAuth()
  const [profile, setProfile] = useState<ProfileResponse | null>(null)
  const [masters, setMasters] = useState<MastersResponse | null>(null)
  const [loadError, setLoadError] = useState<{ notFound: boolean; message: string } | null>(null)
  const [columns, setColumns] = useState<Columns>(() => loadColumns(PROFILE_COLUMNS))

  useEffect(() => {
    if (!token) return
    let cancelled = false
    Promise.all([userId === null ? fetchMyProfile(token) : fetchProfile(userId, token), fetchMasters(token)])
      .then(([loadedProfile, loadedMasters]) => {
        if (cancelled) return
        setProfile(loadedProfile)
        setMasters(loadedMasters)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        const notFound = err instanceof ApiError && err.status === 404
        setLoadError({
          notFound,
          message: err instanceof ApiError ? err.message : 'プロフィールを読み込めませんでした。',
        })
      })
    return () => {
      cancelled = true
    }
  }, [token, userId])

  const handleChangeColumns = (next: Columns) => {
    setColumns(next)
    saveColumns(PROFILE_COLUMNS, next)
  }

  if (loadError?.notFound) {
    return <ProfileNotFound />
  }
  if (loadError) {
    return (
      <main className="profile-page">
        <ProfileHeader isMe={userId === null} />
        <p className="profile-message" role="alert">
          {loadError.message}
        </p>
      </main>
    )
  }
  if (!profile || !masters) {
    return (
      <main className="profile-page">
        <ProfileHeader isMe={userId === null} />
        <p className="profile-message" role="status">
          読み込み中...
        </p>
      </main>
    )
  }
  if (userId !== null && profile.me) {
    return <Navigate to="/profile" replace />
  }

  return (
    <main className="profile-page">
      <ProfileHeader isMe={profile.me} />
      <ProfileSummary profile={profile} masters={masters} />
      {!profile.me && (
        <ProfileActions
          profile={profile}
          onFollowChange={(following, followerCount) =>
            setProfile((prev) => (prev ? { ...prev, followingByMe: following, followerCount } : prev))
          }
        />
      )}
      {profile.me ? (
        <MyPosts profile={profile} columns={columns} onChangeColumns={handleChangeColumns} />
      ) : (
        <OtherPosts profile={profile} columns={columns} onChangeColumns={handleChangeColumns} />
      )}
    </main>
  )
}

/** 自分のプロフィールは右上に三点リーダー（詳細設定へ）、相手のプロフィールは左上に戻るボタン */
function ProfileHeader({ isMe }: { isMe: boolean }) {
  const navigate = useNavigate()
  const location = useLocation()
  return (
    <header className="profile-header">
      {isMe ? (
        <Link to="/settings" className="profile-header-button profile-header-menu" aria-label="詳細設定">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <circle cx="12" cy="5" r="1.8" />
            <circle cx="12" cy="12" r="1.8" />
            <circle cx="12" cy="19" r="1.8" />
          </svg>
        </Link>
      ) : (
        // 通報・ブロック・非表示は未実装のため、相手のプロフィールの三点リーダーは出さない
        <button type="button" className="profile-header-button" aria-label="戻る" onClick={() => goBack(navigate, location, '/')}>
          ‹
        </button>
      )}
    </header>
  )
}

function ProfileSummary({ profile, masters }: { profile: ProfileResponse; masters: MastersResponse }) {
  // 設定されている項目だけを「・」でつなぐ
  const details = [
    profile.heightCm !== null ? `${profile.heightCm}cm` : null,
    masters.genders.find((option) => option.code === profile.gender)?.label,
    masters.ageGroups.find((option) => option.code === profile.ageGroup)?.label,
    profile.bodyType?.name,
    profile.personalColor?.name,
  ].filter((value): value is string => !!value)

  return (
    <section className="profile-summary">
      <div className="profile-identity">
        {profile.profileImageUrl ? (
          <img className="profile-avatar" src={profile.profileImageUrl} alt="プロフィール画像" />
        ) : (
          <span className="profile-avatar profile-avatar-empty" role="img" aria-label="プロフィール画像（未設定）">
            <svg width="34" height="34" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.3" aria-hidden="true">
              <circle cx="12" cy="8.5" r="3.5" />
              <path d="M5 20c1.1-3.4 3.7-5 7-5s5.9 1.6 7 5" strokeLinecap="round" />
            </svg>
          </span>
        )}
        <div className="profile-names">
          <h1 className="profile-display-name">{profile.displayName}</h1>
          <p className="profile-username">@{profile.username}</p>
          {details.length > 0 && <p className="profile-details">{details.join(' ・ ')}</p>}
        </div>
      </div>

      {/* フォロー中・フォロワーの一覧（docs/profile.md「フォロー中一覧」）へ */}
      <div className="profile-counts">
        <Link to={`/users/${profile.id}/followings`} className="profile-count">
          <strong>{profile.followingCount.toLocaleString('ja-JP')}</strong>
          <span>フォロー中</span>
        </Link>
        <Link to={`/users/${profile.id}/followers`} className="profile-count">
          <strong>{profile.followerCount.toLocaleString('ja-JP')}</strong>
          <span>フォロワー</span>
        </Link>
      </div>
    </section>
  )
}

/** 相手のプロフィールの「相談を申し込む」「フォロー」ボタン */
function ProfileActions({
  profile,
  onFollowChange,
}: {
  profile: ProfileResponse
  onFollowChange: (following: boolean, followerCount: number) => void
}) {
  const { token } = useAuth()
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const following = profile.followingByMe === true
  // 「相談を申し込む」ボタンの状態。読み込むまでは押せない状態で表示する。statusVersion を増やすと取り直す
  const [consultation, setConsultation] = useState<ConsultationStatus | null>(null)
  const [statusVersion, setStatusVersion] = useState(0)

  useEffect(() => {
    if (!token) return
    const controller = new AbortController()
    fetchConsultationStatus(profile.id, token, controller.signal)
      .then(setConsultation)
      .catch(() => {
        // 読めなかった場合は押せないままにする（フォローなど他の操作は使える）
      })
    return () => controller.abort()
  }, [profile.id, token, statusVersion])

  // 通信中は押せないようにし、結果はサーバーの応答で確定する（連打しても状態がずれない）
  const handleFollow = async () => {
    if (!token || pending) return
    setPending(true)
    setError(null)
    try {
      const result = await setFollow(profile.id, !following, token)
      onFollowChange(result.following, result.followerCount)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : following ? 'フォローを解除できませんでした。' : 'フォローできませんでした。')
    } finally {
      setPending(false)
    }
  }

  return (
    <div className="profile-actions-wrap">
      <div className="profile-actions">
        <ConsultButton
          userId={profile.id}
          username={profile.username}
          status={consultation}
          label="相談を申し込む"
          icon={
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" aria-hidden="true">
              <path d="M20 12a8 8 0 0 1-11.8 7L4 20l1.1-3.9A8 8 0 1 1 20 12z" />
            </svg>
          }
          className="profile-consult"
          onStatusChanged={() => setStatusVersion((version) => version + 1)}
        />
        <button
          type="button"
          className={`profile-follow${following ? ' profile-follow-active' : ''}`}
          aria-pressed={following}
          disabled={pending}
          onClick={handleFollow}
        >
          {following ? 'フォロー中' : 'フォロー'}
        </button>
      </div>
      {/* 上限に達している相手には「現在、新しい相談を受け付けていません」と表示する（docs/dm.md） */}
      {consultNote(consultation) && <p className="consult-note">{consultNote(consultation)}</p>}
      {error && (
        <p className="profile-action-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

type PostsProps = { profile: ProfileResponse; columns: Columns; onChangeColumns: (columns: Columns) => void }

/** 自分のプロフィールの投稿（タブ：自分の投稿 / お気に入り） */
function MyPosts({ profile, columns, onChangeColumns }: PostsProps) {
  const [tab, setTab] = useState<MyTab>('posts')
  const fetchPage: FetchPostPage =
    tab === 'posts'
      ? (page, token, signal) => fetchUserPosts(profile.id, page, token, signal)
      : (page, token, signal) => fetchMyFavorites(page, token, signal)

  return (
    <>
      <div className="profile-segments" role="tablist" aria-label="表示する投稿">
        {MY_TABS.map((item) => (
          <button
            key={item.key}
            type="button"
            role="tab"
            id={`profile-tab-${item.key}`}
            aria-selected={tab === item.key}
            aria-controls="profile-tab-panel"
            className={`profile-segment${tab === item.key ? ' profile-segment-active' : ''}`}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </button>
        ))}
      </div>
      <div className="profile-toolbar">
        <span className="profile-post-count">{tab === 'posts' ? `投稿 ${profile.postCount.toLocaleString('ja-JP')}件` : ''}</span>
        <ColumnsSwitcher columns={columns} onChange={onChangeColumns} />
      </div>
      <section id="profile-tab-panel" role="tabpanel" aria-labelledby={`profile-tab-${tab}`} className="profile-panel">
        <PostGrid
          key={tab}
          fetchPage={fetchPage}
          columns={columns}
          empty={
            tab === 'posts' ? (
              <EmptyMessage title="まだ投稿がありません">
                <Link to="/post" className="home-empty-action">
                  投稿する
                </Link>
              </EmptyMessage>
            ) : (
              <EmptyMessage title="お気に入りに追加した投稿はありません" />
            )
          }
        />
      </section>
    </>
  )
}

/** 相手のプロフィールの投稿（タブ：投稿 / フォローしている人の投稿をランダムに表示） */
function OtherPosts({ profile, columns, onChangeColumns }: PostsProps) {
  const [tab, setTab] = useState<OtherTab>('posts')
  // 画面を開くたびに並び順を変える。同じ一覧の次のページでは同じ値を使う
  const [seed] = useState(newShuffleSeed)
  const fetchPage: FetchPostPage =
    tab === 'posts'
      ? (page, token, signal) => fetchUserPosts(profile.id, page, token, signal)
      : (page, token, signal) => fetchFollowingPosts(profile.id, seed, page, token, signal)

  return (
    <>
      <div className="home-toolbar profile-tabs-toolbar">
        <div className="home-tabs" role="tablist" aria-label="表示する投稿">
          {OTHER_TABS.map((item) => (
            <button
              key={item.key}
              type="button"
              role="tab"
              id={`profile-tab-${item.key}`}
              aria-selected={tab === item.key}
              aria-controls="profile-tab-panel"
              className={`home-tab${tab === item.key ? ' home-tab-active' : ''}`}
              onClick={() => setTab(item.key)}
            >
              {item.label}
            </button>
          ))}
        </div>
        <ColumnsSwitcher columns={columns} onChange={onChangeColumns} />
      </div>
      <section id="profile-tab-panel" role="tabpanel" aria-labelledby={`profile-tab-${tab}`} className="profile-panel">
        <PostGrid
          key={tab}
          fetchPage={fetchPage}
          columns={columns}
          empty={
            <EmptyMessage
              title={tab === 'posts' ? 'まだ投稿がありません' : 'フォロー中のユーザーの投稿はありません'}
            />
          }
        />
      </section>
    </>
  )
}

function ProfileNotFound() {
  return (
    <main className="profile-page">
      <ProfileHeader isMe={false} />
      <p className="profile-message" role="alert">
        ユーザーが見つかりません。
      </p>
    </main>
  )
}
