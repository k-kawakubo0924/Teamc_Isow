import { Link } from 'react-router'
import './NotFoundPage.css'

/**
 * 存在しない URL を開いたときの画面。
 * ログイン済みの一般利用者が管理画面（/admin）を開いたときも、まったく同じこの画面を出す
 * （管理画面があることを画面から分からないようにするため。docs/admin.md）。
 * そのため、この画面には管理画面に関わる文言・部品を入れないこと
 */
function NotFoundPage() {
  return (
    <main className="not-found-page">
      <h1 className="not-found-title">ページが見つかりません</h1>
      <p className="not-found-text">URLが間違っているか、ページが削除された可能性があります。</p>
      <Link to="/" className="not-found-home">
        ホームへ戻る
      </Link>
    </main>
  )
}

export default NotFoundPage
