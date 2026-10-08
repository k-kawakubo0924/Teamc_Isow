import { Link } from 'react-router'
import type { TimelineItem } from '../../api/posts'
import { formatHeight } from '../../utils/height'

type Props = {
  post: TimelineItem
  /** false なら写真のみ（1列・3列のとき。docs/home.md） */
  detailed: boolean
  onToggleLike: () => void
  onToggleFavorite: () => void
}

/** ホームの投稿一覧の1件（design/home.png のカード） */
export function PostCard({ post, detailed, onToggleLike, onToggleFavorite }: Props) {
  const { author, fashionCategory } = post
  // 写真を押すと投稿の詳細画面へ（1列・2列・3列のどれでも）
  const photo = (
    <Link to={`/posts/${post.id}`} className="home-card-photo-link">
      <img className="home-card-photo" src={post.thumbnailUrl} alt={`@${author.username} の投稿`} loading="lazy" />
    </Link>
  )
  if (!detailed) {
    return <article className="home-card">{photo}</article>
  }

  // 身長が未設定なら表示を省く（docs/home.md）
  const height = formatHeight(author.heightCm)
  const meta = height === null ? fashionCategory.name : `${height} ・ ${fashionCategory.name}`

  return (
    <article className="home-card">
      {photo}
      <div className="home-card-body">
        <div className="home-card-text">
          {/* 押すと投稿者のプロフィールへ（自分の投稿なら /users/{id} から /profile に切り替わる） */}
          <Link to={`/users/${author.id}`} className="home-card-username">
            @{author.username}
          </Link>
          <p className="home-card-meta">{meta}</p>
        </div>
        <div className="home-card-actions">
          <button
            type="button"
            className={`home-card-like${post.likedByMe ? ' home-card-active' : ''}`}
            aria-pressed={post.likedByMe}
            aria-label={`いいね（${post.likeCount}件）`}
            onClick={onToggleLike}
          >
            <HeartIcon filled={post.likedByMe} />
            <span aria-hidden="true">{post.likeCount}</span>
          </button>
          <button
            type="button"
            className={`home-card-favorite${post.favoritedByMe ? ' home-card-active' : ''}`}
            aria-pressed={post.favoritedByMe}
            aria-label="お気に入り"
            onClick={onToggleFavorite}
          >
            <StarIcon filled={post.favoritedByMe} />
          </button>
        </div>
      </div>
    </article>
  )
}

/** いいねのアイコン（投稿のカード・詳細画面で共通） */
export function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 20.5s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.7a4.3 4.3 0 0 1 7.5 2.6c0 5.6-7.5 10.2-7.5 10.2z" />
    </svg>
  )
}

/** お気に入りのアイコン（投稿のカード・詳細画面で共通） */
export function StarIcon({ filled }: { filled: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" aria-hidden="true">
      <path d="m12 3.8 2.5 5.2 5.7.8-4.1 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.1-4 5.7-.8z" />
    </svg>
  )
}
