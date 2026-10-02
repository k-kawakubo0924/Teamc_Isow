import type { PostResponse } from '../../api/posts'

type Props = {
  post: PostResponse
  onContinue: () => void
  onHome: () => void
}

/**
 * 投稿の完了（暫定。docs/post.md「投稿の流れ（暫定）」）。
 * 投稿した内容を見る画面がまだないため、サーバーに保存された内容の要約をここで見せる。
 * 投稿の詳細画面ができたら、完了後はそちらへ移動する形に差し替える
 */
export function PostComplete({ post, onContinue, onHome }: Props) {
  return (
    <main className="post-page">
      <header className="post-header">
        <h1 className="post-title">投稿完了</h1>
      </header>

      <div className="post-body">
        <div className="auth-banner auth-banner-success" role="status">
          <p>投稿しました。</p>
        </div>

        <article className="post-complete-card">
          {/* サーバーに保存された1枚目（一覧のサムネイルになる写真） */}
          <img src={post.imageUrls[0]} alt="投稿した写真の1枚目" />
          <div className="post-complete-text">
            <p className="post-complete-title">{post.title}</p>
            <p className="post-complete-meta">{post.fashionCategory.name}</p>
            <p className="post-complete-meta">{post.tags.map((tag) => tag.name).join(' ／ ')}</p>
            <p className="post-complete-meta">写真 {post.imageUrls.length}枚</p>
          </div>
        </article>
      </div>

      <div className="post-footer post-footer-split">
        <button type="button" className="post-button post-button-secondary" onClick={onHome}>
          ホームへ
        </button>
        <button type="button" className="post-button post-button-primary" onClick={onContinue}>
          続けて投稿する
        </button>
      </div>
    </main>
  )
}
