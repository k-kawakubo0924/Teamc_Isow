import { Link, useSearchParams } from 'react-router'
import { readConditions, toParams } from './searchUrl'
import './search.css'

/**
 * 検索結果（design/Searchresults.png）。画面は別の作業で作るため、今は準備中の表示だけ。
 * 「条件を変更」で、選んだ条件を残したまま検索画面に戻る
 */
function SearchResultsPage() {
  const [searchParams] = useSearchParams()
  const conditions = readConditions(searchParams)

  return (
    <main className="search-page">
      <p className="search-message search-message-spaced" role="status">
        検索結果の画面は準備中です
      </p>
      <Link to={`/search?${toParams({ ...conditions, q: '' })}`} className="search-text-button search-back-link">
        条件を変更
      </Link>
    </main>
  )
}

export default SearchResultsPage
