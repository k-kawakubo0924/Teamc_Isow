import { useEffect, useId, useState, type FormEvent } from 'react'
import type { MasterOption } from '../../api/masters'
import { searchTags, type TagCandidate } from '../../api/tags'
import { MAX_TAGS, normalizeTagName, tagKey, validateNewTag } from '../../validation/postRules'
import { PanelHeader } from './PanelHeader'

/** 入力が止まってから候補を検索するまでの待ち時間（1文字ごとに API を呼ばないため） */
const SEARCH_DELAY_MS = 250

type Props = {
  token: string
  /** 公式タグ（GET /api/masters の tags） */
  officialTags: MasterOption[]
  selected: string[]
  onChange: (tags: string[]) => void
  onClose: () => void
}

/**
 * タグ選択（docs/post.md）。公式タグから選べるほか、手入力でも追加できる。
 * 手入力中は GET /api/tags で既存のタグを候補として出す。
 * 表記ゆれ（Y2K と ｙ２ｋ など）はバックエンドと同じ規則で同じタグとみなし、重複して追加しない
 */
export function TagPanel({ token, officialTags, selected, onChange, onClose }: Props) {
  const inputId = useId()
  const [input, setInput] = useState('')
  // どの入力に対する検索結果かも持ち、今の入力と一致する結果だけを表示する（古い結果を出さないため）
  const [result, setResult] = useState<{ query: string; candidates: TagCandidate[] }>({ query: '', candidates: [] })
  const [error, setError] = useState<string | null>(null)

  const query = normalizeTagName(input)
  const selectedKeys = new Set(selected.map(tagKey))

  useEffect(() => {
    if (query === '') return
    const controller = new AbortController()
    const timer = setTimeout(() => {
      searchTags(query, token, controller.signal)
        .then((candidates) => setResult({ query, candidates }))
        // 中断（次の入力があった）や検索の失敗では候補を出さないだけにし、入力は続けられるようにする
        .catch(() => {})
    }, SEARCH_DELAY_MS)
    return () => {
      clearTimeout(timer)
      controller.abort()
    }
  }, [query, token])

  const add = (name: string): boolean => {
    const normalized = normalizeTagName(name)
    if (selectedKeys.has(tagKey(normalized))) {
      setError(`「${normalized}」はすでに追加しています`)
      return false
    }
    const message = validateNewTag(normalized, selected)
    if (message) {
      setError(message)
      return false
    }
    setError(null)
    onChange([...selected, normalized])
    return true
  }

  const remove = (name: string) => {
    setError(null)
    onChange(selected.filter((tag) => tagKey(tag) !== tagKey(name)))
  }

  const toggleOfficial = (name: string) => {
    if (selectedKeys.has(tagKey(name))) {
      remove(name)
    } else {
      add(name)
    }
  }

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (add(input)) setInput('')
  }

  const handlePickCandidate = (name: string) => {
    if (add(name)) setInput('')
  }

  const visibleCandidates =
    query !== '' && result.query === query ? result.candidates.filter((c) => !selectedKeys.has(tagKey(c.name))) : []

  return (
    <div className="post-panel">
      <PanelHeader title="タグ選択" onClose={onClose} />

      <div className="post-panel-body">
        <div className="post-label-row">
          <span className="post-label">選択中のタグ</span>
          <span className="post-counter">
            {selected.length} / {MAX_TAGS}
          </span>
        </div>
        {selected.length === 0 ? (
          <p className="post-hint">まだタグがありません</p>
        ) : (
          <ul className="post-chips">
            {selected.map((tag) => (
              <li key={tagKey(tag)} className="post-chip post-chip-selected">
                {tag}
                <button type="button" aria-label={`タグ「${tag}」を外す`} onClick={() => remove(tag)}>
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}

        <form className="post-tag-form" onSubmit={handleSubmit}>
          <label htmlFor={inputId} className="post-label">
            タグを入力して追加
          </label>
          <div className="post-tag-input-row">
            <input
              id={inputId}
              className={`post-input${error ? ' post-input-error' : ''}`}
              value={input}
              placeholder="例：古着"
              autoComplete="off"
              enterKeyHint="done"
              onChange={(e) => {
                setInput(e.target.value)
                setError(null)
              }}
            />
            <button type="submit" className="post-button post-button-secondary">
              追加
            </button>
          </div>
          {error && <p className="post-field-error">{error}</p>}

          {visibleCandidates.length > 0 && (
            <ul className="post-candidates" aria-label="入力候補">
              {visibleCandidates.map((candidate) => (
                <li key={candidate.id}>
                  <button type="button" onClick={() => handlePickCandidate(candidate.name)}>
                    {candidate.name}
                    {candidate.official && <span className="post-official">公式</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </form>

        <p className="post-label post-panel-subheading">公式タグから選ぶ</p>
        <ul className="post-chips">
          {officialTags.map((tag) => {
            const isSelected = selectedKeys.has(tagKey(tag.name))
            return (
              <li key={tag.id}>
                <button
                  type="button"
                  className={`post-chip${isSelected ? ' post-chip-selected' : ''}`}
                  aria-pressed={isSelected}
                  onClick={() => toggleOfficial(tag.name)}
                >
                  {tag.name}
                </button>
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}
