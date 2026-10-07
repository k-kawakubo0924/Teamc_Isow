import { useEffect, useId, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { fetchMasters, type EnumOption, type MastersResponse } from '../../api/masters'
import { searchPosts, searchUsers, type UserSearchItem } from '../../api/search'
import { useAuth } from '../../auth/authContext'
import { useLoadMoreOnScroll, usePagedList } from '../../hooks/usePagedList'
import { UserRow } from '../follow/UserRow'
import { useConsultStatuses, useFollowToggle } from '../follow/useUserRowActions'
import { loadColumns, saveColumns, SEARCH_COLUMNS, type Columns } from '../home/columnSetting'
import { ColumnsSwitcher, EmptyMessage, PostGrid } from '../home/PostGrid'
import { readConditions, searchResultsUrl, toParams, type SearchConditions } from './searchUrl'
import './search.css'

/**
 * 検索結果（docs/search.md、design/Searchresults.png）。条件は URL のクエリで受け取る。
 * 投稿はホームと同じグリッド、ユーザーはフォロー中一覧と同じリストで表示する。
 */
function SearchResultsPage() {
  const [searchParams] = useSearchParams()
  const queryString = searchParams.toString()
  // 条件が変わったら（検索し直したら）、入力欄・件数・読み込んだ結果を作り直す
  return <SearchResultsView key={queryString} conditions={readConditions(searchParams)} />
}

function SearchResultsView({ conditions }: { conditions: SearchConditions }) {
  const navigate = useNavigate()
  const { token } = useAuth()
  const inputId = useId()
  const [keyword, setKeyword] = useState(conditions.q)
  const [totalCount, setTotalCount] = useState<number | null>(null)
  const [masters, setMasters] = useState<MastersResponse | null>(null)
  const [columns, setColumns] = useState<Columns>(() => loadColumns(SEARCH_COLUMNS))

  // 条件の札の名前と、ユーザーの性別の表示名は選択肢の一覧から引く
  useEffect(() => {
    if (!token) return
    let cancelled = false
    fetchMasters(token)
      .then((result) => {
        if (!cancelled) setMasters(result)
      })
      .catch(() => {
        // 読めなくても結果は表示できる（札の名前・性別の表示が省かれるだけ）
      })
    return () => {
      cancelled = true
    }
  }, [token])

  const isUsers = conditions.type === 'users'
  /** 条件を保ったまま検索画面に戻る URL（キーワードも検索欄に入れて戻る） */
  const changeUrl = `/search?${toParams(conditions)}`

  // 検索し直しても結果画面が履歴に積み重ならないよう、URL を置き換える
  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    navigate(searchResultsUrl({ ...conditions, q: keyword }), { replace: true })
  }

  const handleChangeColumns = (next: Columns) => {
    setColumns(next)
    saveColumns(SEARCH_COLUMNS, next)
  }

  const empty = (
    <EmptyMessage title={isUsers ? emptyUsersTitle(conditions.q) : '条件に一致する投稿はありません'}>
      <p className="search-empty-hint">キーワードや条件を変えてお試しください</p>
      <Link to={changeUrl} className="home-empty-action">
        条件を変更
      </Link>
    </EmptyMessage>
  )

  return (
    <main className="search-page">
      <form className="search-form" role="search" onSubmit={handleSubmit}>
        <label className="search-visually-hidden" htmlFor={inputId}>
          検索キーワード
        </label>
        <input
          id={inputId}
          className="search-input"
          type="search"
          enterKeyHint="search"
          maxLength={100}
          placeholder={isUsers ? 'ユーザー名・表示名で検索' : 'ユーザー名・キーワードで検索'}
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
        <button type="submit" className="search-submit" aria-label="検索">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
            <circle cx="11" cy="11" r="6.5" />
            <path d="m20 20-4.2-4.2" />
          </svg>
        </button>
      </form>

      <div className="search-result-conditions">
        <ConditionLabels conditions={conditions} masters={masters} />
        <Link to={changeUrl} className="search-change">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M4 7h13m0 0-3-3m3 3-3 3M20 17H7m0 0 3-3m-3 3 3 3" />
          </svg>
          変更
        </Link>
      </div>

      <section className="search-panel" aria-labelledby="search-result-count">
        <div className="search-result-header">
          <h1 className="search-result-count" id="search-result-count">
            検索結果{totalCount !== null && ` ${totalCount.toLocaleString('ja-JP')}件`}
          </h1>
          {/* 選んだ列は端末ごとに記憶する（ホーム・プロフィールとは別） */}
          {!isUsers && <ColumnsSwitcher columns={columns} onChange={handleChangeColumns} />}
        </div>

        {isUsers ? (
          <UserResults q={conditions.q} genders={masters?.genders ?? []} onTotalCount={setTotalCount} empty={empty} />
        ) : (
          <PostGrid
            fetchPage={(page, authToken, signal) =>
              searchPosts(conditions, page, authToken, signal).then((result) => {
                setTotalCount(result.totalCount)
                return result
              })
            }
            columns={columns}
            empty={empty}
          />
        )}
      </section>
    </main>
  )
}

function emptyUsersTitle(q: string): string {
  return q.trim() === '' ? 'ユーザーが見つかりません' : `「${q.trim()}」に一致するユーザーはいません`
}

/** 検索した条件の札（design/Searchresults.png の上部）。条件がなければ何も出さない */
function ConditionLabels({ conditions, masters }: { conditions: SearchConditions; masters: MastersResponse | null }) {
  const labels: { kind: string; name: string }[] = []
  if (conditions.type === 'users') {
    labels.push({ kind: 'アカウント', name: 'ユーザーを検索' })
  } else {
    const category = masters?.fashionCategories.find((c) => c.id === conditions.categoryId)
    const ageGroup = masters?.ageGroups.find((a) => a.code === conditions.ageGroup)
    if (category) labels.push({ kind: 'ジャンル', name: category.name })
    if (ageGroup) labels.push({ kind: '年代', name: ageGroup.label })
  }

  return (
    <ul className="search-result-labels" aria-label="検索した条件">
      {labels.map((label) => (
        <li key={label.kind} className="search-tag search-result-label">
          <span className="search-tag-kind">{label.kind}</span>
          <span>{label.name}</span>
        </li>
      ))}
    </ul>
  )
}

/** ユーザーの検索結果（フォロー中一覧と同じリスト）。末尾が見えてきたら次のページを読み込む */
function UserResults({
  q,
  genders,
  onTotalCount,
  empty,
}: {
  q: string
  genders: EnumOption[]
  onTotalCount: (count: number) => void
  empty: ReactNode
}) {
  const { token } = useAuth()
  const { consultStatuses, loadConsultStatuses } = useConsultStatuses()
  const { state, loadMore, retry, updateItem } = usePagedList<UserSearchItem>(
    (page, authToken, signal) =>
      searchUsers(q, page, authToken, signal).then((result) => {
        onTotalCount(result.totalCount)
        // 一覧は先に表示し、「相談する」ボタンの状態は後から反映する
        loadConsultStatuses(result.users.map((user) => user.id), authToken, signal)
        return { items: result.users, hasNext: result.hasNext }
      }),
    token,
  )
  const sentinelRef = useLoadMoreOnScroll(loadMore)
  const { pending, error, handleFollow } = useFollowToggle(token, updateItem)

  const users = state.items
  const isEmpty = users.length === 0 && !state.hasNext && state.status === 'idle'

  return (
    <>
      {error && (
        <p className="follow-error" role="alert">
          {error}
        </p>
      )}
      {isEmpty ? (
        empty
      ) : (
        <ul className="follow-list">
          {users.map((user) => (
            <li key={user.id}>
              <UserRow
                user={user}
                // 検索結果に自分は含まれない
                isMe={false}
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

export default SearchResultsPage
