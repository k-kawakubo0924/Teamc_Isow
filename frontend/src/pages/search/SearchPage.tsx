import { useCallback, useEffect, useId, useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { fetchMasters, type EnumOption, type MasterOption } from '../../api/masters'
import {
  deleteAllSearchHistory,
  deleteSearchHistory,
  fetchSearchHistory,
  fetchSuggestions,
  type SearchHistoryItem,
  type SuggestionWord,
} from '../../api/search'
import { useAuth } from '../../auth/authContext'
import { SelectSheet } from './SelectSheet'
import { readConditions, searchResultsUrl, toParams, type SearchConditions } from './searchUrl'
import './search.css'

type Sheet = 'genre' | 'age' | null

/**
 * 検索画面（docs/search.md、design/Searchscreen.png・SearchGenreSelectionScreen.png）。
 *
 * <p>検索項目は「ジャンル別」「年代別」「アカウント」。ジャンルと年代は組み合わせられ、アカウント（ユーザーの検索）は単独で使う。
 * 選んだ条件は URL のクエリに持つ（検索結果画面から戻ったときに残すため）。
 * 検索項目が選ばれていれば候補ワードを、選ばれていなければ検索履歴を表示する。
 */
function SearchPage() {
  const navigate = useNavigate()
  const { token } = useAuth()
  const inputId = useId()
  const [searchParams, setSearchParams] = useSearchParams()
  const conditions = readConditions(searchParams)
  const [keyword, setKeyword] = useState(conditions.q)
  const [sheet, setSheet] = useState<Sheet>(null)

  const [categories, setCategories] = useState<MasterOption[]>([])
  const [ageGroups, setAgeGroups] = useState<EnumOption[]>([])
  const [mastersError, setMastersError] = useState<string | null>(null)

  const isUsers = conditions.type === 'users'
  const hasConditions = isUsers || conditions.categoryId !== null || conditions.ageGroup !== null
  const categoryName = categories.find((c) => c.id === conditions.categoryId)?.name ?? null
  const ageGroupLabel = ageGroups.find((a) => a.code === conditions.ageGroup)?.label ?? null

  useEffect(() => {
    if (!token) return
    let cancelled = false
    fetchMasters(token)
      .then((masters) => {
        if (cancelled) return
        setCategories(masters.fashionCategories)
        setAgeGroups(masters.ageGroups)
      })
      .catch((err: unknown) => {
        if (!cancelled) setMastersError(err instanceof Error ? err.message : String(err))
      })
    return () => {
      cancelled = true
    }
  }, [token])

  /** 条件を URL に反映する（「戻る」で1つずつ条件を戻すことはしないため、履歴を置き換える） */
  const updateConditions = (next: Partial<SearchConditions>) => {
    setSearchParams(toParams({ ...conditions, q: '', ...next }), { replace: true })
  }

  const search = (next: SearchConditions) => {
    navigate(searchResultsUrl(next))
  }

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    // キーワードも条件もない投稿の検索は、何を探すか決まっていないため行わない
    if (keyword.trim() === '' && !hasConditions) return
    search({ ...conditions, q: keyword })
  }

  /** 履歴は、検索項目を選んでいないときに出すため、その状態のまま（投稿の）キーワード検索を行う */
  const handleHistorySelect = (item: SearchHistoryItem) => {
    setKeyword(item.keyword)
    search({ type: 'posts', q: item.keyword, categoryId: null, ageGroup: null })
  }

  /** 候補ワードは、選んでいる条件のままキーワードにして投稿を検索する */
  const handleSuggestionSelect = (word: string) => {
    setKeyword(word)
    search({ ...conditions, q: word })
  }

  const closeSheet = useCallback(() => setSheet(null), [])

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

      {mastersError && (
        <p className="search-error" role="alert">
          {mastersError}
        </p>
      )}

      <div className="search-filters">
        <FilterButton
          label="ジャンル別"
          value={categoryName}
          disabled={categories.length === 0}
          onClick={() => setSheet('genre')}
        />
        <FilterButton
          label="年代別"
          value={ageGroupLabel}
          disabled={ageGroups.length === 0}
          onClick={() => setSheet('age')}
        />
        <button
          type="button"
          className={`search-filter${isUsers ? ' search-filter-active' : ''}`}
          aria-pressed={isUsers}
          // アカウントはジャンル・年代と同時に選べないため、選ぶとジャンル・年代を外す
          onClick={() => updateConditions(isUsers
            ? { type: 'posts' }
            : { type: 'users', categoryId: null, ageGroup: null })}
        >
          <span className="search-filter-label">アカウント</span>
          <span className="search-filter-value">{isUsers ? 'ユーザーを検索中' : 'ユーザーを探す'}</span>
        </button>
      </div>

      <section className="search-panel">
        {hasConditions ? (
          <>
            <SelectedConditions
              categoryName={categoryName}
              ageGroupLabel={ageGroupLabel}
              isUsers={isUsers}
              onRemoveCategory={() => updateConditions({ categoryId: null })}
              onRemoveAgeGroup={() => updateConditions({ ageGroup: null })}
              onRemoveUsers={() => updateConditions({ type: 'posts' })}
              onClear={() => updateConditions({ type: 'posts', categoryId: null, ageGroup: null })}
            />
            {/* 候補ワードはタグのため、ユーザーの検索では出さない */}
            {!isUsers && token && (
              <Suggestions token={token} categoryId={conditions.categoryId} onSelect={handleSuggestionSelect} />
            )}
          </>
        ) : (
          token && <SearchHistory token={token} onSelect={handleHistorySelect} />
        )}
      </section>

      {sheet === 'genre' && (
        <SelectSheet
          title="ジャンル"
          options={categories.map((c) => ({ value: String(c.id), label: c.name }))}
          selected={conditions.categoryId === null ? null : String(conditions.categoryId)}
          onApply={(value) => {
            updateConditions({ type: 'posts', categoryId: value === null ? null : Number(value) })
            setSheet(null)
          }}
          onClose={closeSheet}
        />
      )}
      {sheet === 'age' && (
        <SelectSheet
          title="年代"
          options={ageGroups.map((a) => ({ value: a.code, label: a.label }))}
          selected={conditions.ageGroup}
          onApply={(value) => {
            updateConditions({ type: 'posts', ageGroup: value })
            setSheet(null)
          }}
          onClose={closeSheet}
        />
      )}
    </main>
  )
}

/** 検索項目のボタン（ジャンル別・年代別）。選んでいれば件数のバッジと、選んだものの名前を出す */
function FilterButton({
  label,
  value,
  disabled,
  onClick,
}: {
  label: string
  value: string | null
  disabled: boolean
  onClick: () => void
}) {
  return (
    <button
      type="button"
      className={`search-filter${value !== null ? ' search-filter-active' : ''}`}
      aria-haspopup="dialog"
      disabled={disabled}
      onClick={onClick}
    >
      <span className="search-filter-label">
        {label}
        {value !== null && (
          <span className="search-filter-badge" aria-label="1件選択中">
            1
          </span>
        )}
      </span>
      <span className="search-filter-value">
        {value ?? '選択する'}
        <span aria-hidden="true"> ▾</span>
      </span>
    </button>
  )
}

/** 選択中の条件（design/Searchscreen.png）。× で1つずつ外せる */
function SelectedConditions({
  categoryName,
  ageGroupLabel,
  isUsers,
  onRemoveCategory,
  onRemoveAgeGroup,
  onRemoveUsers,
  onClear,
}: {
  categoryName: string | null
  ageGroupLabel: string | null
  isUsers: boolean
  onRemoveCategory: () => void
  onRemoveAgeGroup: () => void
  onRemoveUsers: () => void
  onClear: () => void
}) {
  return (
    <div className="search-section">
      <div className="search-section-header">
        <h2 className="search-section-title">選択中の条件</h2>
        <button type="button" className="search-text-button" onClick={onClear}>
          すべて解除
        </button>
      </div>
      <ul className="search-tags">
        {categoryName !== null && (
          <ConditionTag kind="ジャンル" name={categoryName} onRemove={onRemoveCategory} />
        )}
        {ageGroupLabel !== null && <ConditionTag kind="年代" name={ageGroupLabel} onRemove={onRemoveAgeGroup} />}
        {isUsers && <ConditionTag kind="アカウント" name="ユーザーを検索" onRemove={onRemoveUsers} />}
      </ul>
    </div>
  )
}

function ConditionTag({ kind, name, onRemove }: { kind: string; name: string; onRemove: () => void }) {
  return (
    <li className="search-tag">
      <span className="search-tag-kind">{kind}</span>
      <span>{name}</span>
      <button type="button" className="search-tag-remove" aria-label={`${kind}「${name}」を外す`} onClick={onRemove}>
        ×
      </button>
    </li>
  )
}

/**
 * 候補ワード。ジャンルを選んでいればその種類でよく使われているタグ、年代だけなら全投稿でよく使われているタグ。
 * 0件・読み込めなかった場合は欄ごと出さない（候補は補助的な表示のため）
 */
function Suggestions({
  token,
  categoryId,
  onSelect,
}: {
  token: string
  categoryId: number | null
  onSelect: (word: string) => void
}) {
  // どの条件に対する結果かも持ち、今の条件と一致する結果だけを表示する（古い結果を出さないため）
  const [result, setResult] = useState<{ categoryId: number | null; words: SuggestionWord[] } | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetchSuggestions(categoryId, token, controller.signal)
      .then((words) => setResult({ categoryId, words }))
      .catch(() => {
        // 中断（条件が変わった）や失敗では候補を出さないだけにする
      })
    return () => controller.abort()
  }, [categoryId, token])

  if (result === null || result.categoryId !== categoryId || result.words.length === 0) return null

  return (
    <div className="search-section">
      <h2 className="search-section-title">候補ワード</h2>
      <ul className="search-words">
        {result.words.map((word) => (
          <li key={word.name}>
            <button type="button" className="search-word" onClick={() => onSelect(word.name)}>
              {word.name}
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** 検索履歴（新しい順）。押すとその語で検索し、× で1件ずつ・「すべて削除」でまとめて削除できる */
function SearchHistory({ token, onSelect }: { token: string; onSelect: (item: SearchHistoryItem) => void }) {
  const [histories, setHistories] = useState<SearchHistoryItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(() => {
    return fetchSearchHistory(token)
      .then((items) => {
        setHistories(items)
        setError(null)
      })
      .catch((err: unknown) => setError(err instanceof Error ? err.message : String(err)))
  }, [token])

  useEffect(() => {
    void load()
  }, [load])

  // 削除は先に画面から消し、失敗したら読み込み直して元に戻す
  const handleDelete = (id: number) => {
    setHistories((prev) => prev?.filter((item) => item.id !== id) ?? prev)
    deleteSearchHistory(id, token).catch((err: unknown) => {
      setError(err instanceof Error ? err.message : String(err))
      void load()
    })
  }

  const handleDeleteAll = () => {
    setHistories([])
    deleteAllSearchHistory(token).catch((err: unknown) => {
      setError(err instanceof Error ? err.message : String(err))
      void load()
    })
  }

  return (
    <div className="search-section">
      <div className="search-section-header">
        <h2 className="search-section-title">検索履歴</h2>
        {histories !== null && histories.length > 0 && (
          <button type="button" className="search-text-button" onClick={handleDeleteAll}>
            すべて削除
          </button>
        )}
      </div>

      {error && (
        <p className="search-error" role="alert">
          {error}
        </p>
      )}
      {histories === null ? (
        !error && <p className="search-message">読み込み中...</p>
      ) : histories.length === 0 ? (
        <p className="search-message">検索履歴はありません</p>
      ) : (
        <ul className="search-histories">
          {histories.map((item) => (
            <li key={item.id} className="search-history">
              <button type="button" className="search-history-keyword" onClick={() => onSelect(item)}>
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" aria-hidden="true">
                  <circle cx="12" cy="12" r="8" />
                  <path d="M12 8v4l2.5 2" />
                </svg>
                <span>{item.keyword}</span>
              </button>
              <button
                type="button"
                className="search-history-delete"
                aria-label={`「${item.keyword}」を履歴から削除`}
                onClick={() => handleDelete(item.id)}
              >
                ×
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default SearchPage
