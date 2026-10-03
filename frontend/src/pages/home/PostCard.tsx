import type { TimelineItem } from '../../api/posts'

/**
 * ホームの投稿一覧の1件（design/home.png のカード）。
 * いいね・お気に入りは、今は状態の表示だけ（押せるようにするのは次の段階）
 */
export function PostCard({ post }: { post: TimelineItem }) {
  const { author, fashionCategory } = post
  // 身長が未設定なら表示を省く（docs/home.md）
  const meta = author.heightCm === null ? fashionCategory.name : `${author.heightCm}cm ・ ${fashionCategory.name}`

  return (
    <article className="home-card">
      <img className="home-card-photo" src={post.thumbnailUrl} alt={`@${author.username} の投稿`} loading="lazy" />
      <div className="home-card-body">
        <div className="home-card-text">
          <p className="home-card-username">@{author.username}</p>
          <p className="home-card-meta">{meta}</p>
        </div>
        <div className="home-card-actions">
          <span
            className={`home-card-like${post.likedByMe ? ' home-card-active' : ''}`}
            role="img"
            aria-label={`いいね ${post.likeCount}件${post.likedByMe ? '（いいね済み）' : ''}`}
          >
            <HeartIcon filled={post.likedByMe} />
            <span aria-hidden="true">{post.likeCount}</span>
          </span>
          <span
            className={`home-card-favorite${post.favoritedByMe ? ' home-card-active' : ''}`}
            role="img"
            aria-label={post.favoritedByMe ? 'お気に入り登録済み' : 'お気に入り未登録'}
          >
            <StarIcon filled={post.favoritedByMe} />
          </span>
        </div>
      </div>
    </article>
  )
}

function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" aria-hidden="true">
      <path d="M12 20.5s-7.5-4.6-7.5-10.2A4.3 4.3 0 0 1 12 7.7a4.3 4.3 0 0 1 7.5 2.6c0 5.6-7.5 10.2-7.5 10.2z" />
    </svg>
  )
}

function StarIcon({ filled }: { filled: boolean }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" aria-hidden="true">
      <path d="m12 3.8 2.5 5.2 5.7.8-4.1 4 1 5.7-5.1-2.7-5.1 2.7 1-5.7-4.1-4 5.7-.8z" />
    </svg>
  )
}
