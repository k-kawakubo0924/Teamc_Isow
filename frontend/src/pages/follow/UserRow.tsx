import { Link } from 'react-router'
import type { ConsultationStatus } from '../../api/consultations'
import type { EnumOption } from '../../api/masters'
import { ConsultButton } from '../../components/consult/ConsultButton'
import { consultNote } from '../../components/consult/consultLabels'
import { timeAgo } from '../../utils/timeAgo'
import './follow.css'

/** ユーザーの1行に出す内容（フォロー中一覧・ユーザーの検索結果で共通）。未設定の項目は null */
export type UserRowItem = {
  id: number
  username: string
  profileImageUrl: string | null
  heightCm: number | null
  /** GET /api/masters の genders の code */
  gender: string | null
  /** ログイン中のユーザーがこの人をフォローしているか */
  followingByMe: boolean
  /** フォローした日時。フォロー中一覧のときだけ渡し、「〇日前からフォロー」と表示する */
  followedAt?: string
}

/** ユーザーの1行（design/Following List.png）。自分の行にはボタンを出さない */
export function UserRow({
  user,
  isMe,
  genders,
  pending,
  consultStatus,
  onFollow,
  onConsultStatusChanged,
}: {
  user: UserRowItem
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
          {user.followedAt !== undefined && <span className="follow-since">{timeAgo(user.followedAt)}からフォロー</span>}
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
