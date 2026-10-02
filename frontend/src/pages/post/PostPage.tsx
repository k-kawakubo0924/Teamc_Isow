import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/client'
import { fetchMasters, type MastersResponse } from '../../api/masters'
import { createPost } from '../../api/posts'
import { useAuth } from '../../auth/authContext'
import {
  INVALID_INPUT_MESSAGE,
  MAX_DESCRIPTION_LENGTH,
  MAX_IMAGES,
  MAX_REFERENCE_URL_LENGTH,
  MAX_TITLE_LENGTH,
  MAX_WORN_ITEMS_LENGTH,
  validateImageFile,
  validatePost,
  type PostField,
  type PostFieldErrors,
  type PostForm,
} from '../../validation/postRules'
import { ErrorBanner } from '../ErrorBanner'
import { ImagePicker, type SelectedImage } from './ImagePicker'
import { PanelHeader } from './PanelHeader'
import { TagPanel } from './TagPanel'
import '../auth.css'
import './post.css'

const EMPTY_FORM: PostForm = {
  title: '',
  fashionCategoryId: null,
  tags: [],
  wornItems: '',
  description: '',
  referenceUrl: '',
}

/** 入力画面の上に重ねて表示する選択・入力のパネル（docs/post.md の「別のページに移動して選択する」部分） */
type Panel = 'fashion' | 'tags' | 'wornItems' | 'referenceUrl'

type MastersState =
  | { phase: 'loading' }
  | { phase: 'ready'; masters: MastersResponse }
  | { phase: 'error'; message: string }

/**
 * 投稿作成の入力画面（docs/post.md、design/Post Creation Screen.png）。
 * 選択・入力のパネルは URL を分けず同じページ内で切り替える（移動で入力途中の写真や文字が消えないようにするため）。
 * 確認画面・下書き保存はまだ作っていない。
 */
function PostPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  const titleId = useId()
  const descriptionId = useId()

  const [masters, setMasters] = useState<MastersState>({ phase: 'loading' })
  const [images, setImages] = useState<SelectedImage[]>([])
  const [form, setForm] = useState<PostForm>(EMPTY_FORM)
  const [panel, setPanel] = useState<Panel | null>(null)
  const [fieldErrors, setFieldErrors] = useState<PostFieldErrors>({})
  const [bannerMessage, setBannerMessage] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const nextImageId = useRef(1)

  // ---- 選択肢の読み込み ----

  // 「再読み込み」を押すたびに増やし、読み込みをやり直すきっかけにする
  const [mastersRequest, setMastersRequest] = useState(0)

  useEffect(() => {
    // この画面は RequireAuth の内側にあるため、token は必ずある
    if (!token) return
    let cancelled = false
    fetchMasters(token)
      .then((result) => {
        if (!cancelled) setMasters({ phase: 'ready', masters: result })
      })
      .catch((err: unknown) => {
        if (!cancelled) setMasters({ phase: 'error', message: err instanceof Error ? err.message : String(err) })
      })
    return () => {
      cancelled = true
    }
  }, [token, mastersRequest])

  const handleReloadMasters = () => {
    setMasters({ phase: 'loading' })
    setMastersRequest((count) => count + 1)
  }

  // ---- 画面を離れるときに、プレビュー用の URL を解放する ----

  const imagesRef = useRef(images)
  useEffect(() => {
    imagesRef.current = images
  }, [images])
  useEffect(() => () => imagesRef.current.forEach((image) => URL.revokeObjectURL(image.previewUrl)), [])

  // ---- 入力 ----

  const clearFieldError = (name: PostField) => {
    setFieldErrors((prev) => {
      if (!(name in prev)) return prev
      const { [name]: _removed, ...rest } = prev
      return rest
    })
  }

  const updateForm = <K extends keyof PostForm>(name: K, value: PostForm[K]) => {
    setForm((prev) => ({ ...prev, [name]: value }))
    clearFieldError(name)
    setSuccessMessage(null)
  }

  /** 選んだ時点で形式・サイズ・枚数を確認し、条件を満たすものだけを追加する（満たさないものは送信しない） */
  const handleAddImages = (files: File[]) => {
    const messages: string[] = []
    const accepted: SelectedImage[] = []
    for (const file of files) {
      const message = validateImageFile(file)
      if (message) {
        messages.push(message)
        continue
      }
      if (images.length + accepted.length >= MAX_IMAGES) {
        messages.push(`写真は${MAX_IMAGES}枚まで選択できます。${MAX_IMAGES}枚を超えた分は追加していません。`)
        break
      }
      accepted.push({ id: nextImageId.current++, file, previewUrl: URL.createObjectURL(file) })
    }
    setImages((prev) => [...prev, ...accepted])
    setSuccessMessage(null)
    if (messages.length > 0) {
      setFieldErrors((prev) => ({ ...prev, images: messages.join('\n') }))
    } else {
      clearFieldError('images')
    }
  }

  const handleRemoveImage = (id: number) => {
    const target = images.find((image) => image.id === id)
    if (target) URL.revokeObjectURL(target.previewUrl)
    setImages((prev) => prev.filter((image) => image.id !== id))
    clearFieldError('images')
  }

  const handleMoveImage = (id: number, direction: -1 | 1) => {
    setImages((prev) => {
      const index = prev.findIndex((image) => image.id === id)
      const to = index + direction
      if (index < 0 || to < 0 || to >= prev.length) return prev
      const next = [...prev]
      ;[next[index], next[to]] = [next[to], next[index]]
      return next
    })
  }

  // ---- 送信 ----

  const resetAll = () => {
    // 送信中に写真を追加・削除した場合も含め、最新の一覧を解放する
    imagesRef.current.forEach((image) => URL.revokeObjectURL(image.previewUrl))
    setImages([])
    setForm(EMPTY_FORM)
    setFieldErrors({})
  }

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!token || submitting) return

    setSuccessMessage(null)
    const errors = validatePost(form, images.length)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      setBannerMessage(INVALID_INPUT_MESSAGE)
      window.scrollTo({ top: 0 })
      return
    }

    setSubmitting(true)
    setBannerMessage(null)
    try {
      await createPost(
        {
          images: images.map((image) => image.file),
          title: form.title.trim(),
          // validatePost で選択済みを確認している
          fashionCategoryId: form.fashionCategoryId as number,
          tags: form.tags,
          wornItems: form.wornItems.trim(),
          description: form.description.trim(),
          referenceUrl: form.referenceUrl.trim(),
        },
        token,
      )
      // 投稿の詳細画面ができたら、そちらへ移動する
      resetAll()
      setSuccessMessage('投稿しました。')
    } catch (err) {
      if (err instanceof ApiError && (err.status === 400 || err.status === 413) && err.body) {
        // 入力エラー・ファイルが大きすぎる場合は、該当する欄に警告文を出す
        setFieldErrors(err.body.errors as PostFieldErrors)
        setBannerMessage(err.body.message)
      } else {
        setBannerMessage(err instanceof Error ? err.message : String(err))
      }
    } finally {
      setSubmitting(false)
      window.scrollTo({ top: 0 })
    }
  }

  // 直接 /post を開いた場合は戻る先が無いため、ホームへ移動する
  const handleBack = () => (location.key === 'default' ? navigate('/') : navigate(-1))

  // ---- 表示 ----

  if (masters.phase !== 'ready') {
    return (
      <main className="post-page">
        <PageHeader onBack={handleBack} />
        <div className="post-body">
          {masters.phase === 'loading' ? (
            <p className="post-hint">読み込み中...</p>
          ) : (
            <>
              <ErrorBanner message={masters.message} />
              <button type="button" className="post-button post-button-secondary" onClick={handleReloadMasters}>
                再読み込み
              </button>
            </>
          )}
        </div>
      </main>
    )
  }

  const { fashionCategories, tags: officialTags } = masters.masters
  const selectedFashion = fashionCategories.find((c) => c.id === form.fashionCategoryId)

  if (panel === 'fashion') {
    return (
      <main className="post-page">
        <div className="post-panel">
          <PanelHeader title="ファッション選択" onClose={() => setPanel(null)} />
          <ul className="post-panel-body post-options" role="radiogroup" aria-label="ファッションの種類">
            {fashionCategories.map((category) => (
              <li key={category.id}>
                <button
                  type="button"
                  role="radio"
                  aria-checked={category.id === form.fashionCategoryId}
                  className="post-option"
                  onClick={() => {
                    updateForm('fashionCategoryId', category.id)
                    setPanel(null)
                  }}
                >
                  {category.name}
                  {category.id === form.fashionCategoryId && <span aria-hidden="true">✓</span>}
                </button>
              </li>
            ))}
          </ul>
        </div>
      </main>
    )
  }

  if (panel === 'tags') {
    return (
      <main className="post-page">
        <TagPanel
          token={token as string}
          officialTags={officialTags}
          selected={form.tags}
          onChange={(tags) => updateForm('tags', tags)}
          onClose={() => setPanel(null)}
        />
      </main>
    )
  }

  if (panel === 'wornItems' || panel === 'referenceUrl') {
    const isWornItems = panel === 'wornItems'
    return (
      <main className="post-page">
        <div className="post-panel">
          <PanelHeader title={isWornItems ? '着用アイテム' : '参考情報'} onClose={() => setPanel(null)} />
          <div className="post-panel-body">
            {isWornItems ? (
              <TextField
                label="着用アイテム（任意）"
                multiline
                value={form.wornItems}
                maxLength={MAX_WORN_ITEMS_LENGTH}
                placeholder={'例：\nアウター：古着屋で購入（サイズL）\nパンツ：ブラックスラックス'}
                error={fieldErrors.wornItems}
                onChange={(value) => updateForm('wornItems', value)}
              />
            ) : (
              <TextField
                label="参考情報のURL（任意）"
                value={form.referenceUrl}
                maxLength={MAX_REFERENCE_URL_LENGTH}
                placeholder="https://example.com/item"
                type="url"
                hint="どこのサイトで売っているかなど。http:// または https:// で始まるURLを1つ入力できます。"
                error={fieldErrors.referenceUrl}
                onChange={(value) => updateForm('referenceUrl', value)}
              />
            )}
          </div>
        </div>
      </main>
    )
  }

  return (
    <main className="post-page">
      <PageHeader onBack={handleBack} />

      <form className="post-body" noValidate onSubmit={handleSubmit}>
        {bannerMessage && <ErrorBanner message={bannerMessage} />}
        {successMessage && (
          <div className="auth-banner auth-banner-success" role="status">
            <p>{successMessage}</p>
          </div>
        )}

        <ImagePicker
          images={images}
          error={fieldErrors.images}
          onAdd={handleAddImages}
          onRemove={handleRemoveImage}
          onMove={handleMoveImage}
        />

        <section className="post-section">
          <label htmlFor={titleId} className="post-label">
            題名 <span className="post-required">必須</span>
          </label>
          <input
            id={titleId}
            className={`post-input${fieldErrors.title ? ' post-input-error' : ''}`}
            value={form.title}
            placeholder="例：秋の羽織りもの"
            onChange={(e) => updateForm('title', e.target.value)}
          />
          <Counter value={form.title} max={MAX_TITLE_LENGTH} />
          {fieldErrors.title && <p className="post-field-error">{fieldErrors.title}</p>}
        </section>

        <div className="post-rows">
          <SelectRow
            label="ファッション選択"
            required
            summary={selectedFashion?.name}
            emptyText="未選択"
            error={fieldErrors.fashionCategoryId}
            onClick={() => setPanel('fashion')}
          />
          <SelectRow
            label="タグ選択"
            required
            summary={summarizeTags(form.tags)}
            emptyText="未選択"
            error={fieldErrors.tags}
            onClick={() => setPanel('tags')}
          />
          <SelectRow
            label="着用アイテム"
            summary={form.wornItems.trim() || undefined}
            emptyText="未設定"
            error={fieldErrors.wornItems}
            onClick={() => setPanel('wornItems')}
          />
        </div>

        <section className="post-section">
          <label htmlFor={descriptionId} className="post-label">
            投稿説明 <span className="post-required">必須</span>
          </label>
          <textarea
            id={descriptionId}
            className={`post-input post-textarea${fieldErrors.description ? ' post-input-error' : ''}`}
            value={form.description}
            rows={6}
            placeholder="コーディネートのポイントや、選んだ理由など"
            onChange={(e) => updateForm('description', e.target.value)}
          />
          <Counter value={form.description} max={MAX_DESCRIPTION_LENGTH} />
          {fieldErrors.description && <p className="post-field-error">{fieldErrors.description}</p>}
        </section>

        <div className="post-rows">
          <SelectRow
            label="参考情報"
            summary={form.referenceUrl.trim() || undefined}
            emptyText="未設定"
            error={fieldErrors.referenceUrl}
            onClick={() => setPanel('referenceUrl')}
          />
        </div>

        <div className="post-footer">
          <button type="submit" className="post-button post-button-primary" disabled={submitting}>
            {submitting ? '投稿中...' : '投稿'}
          </button>
        </div>
      </form>
    </main>
  )
}

function PageHeader({ onBack }: { onBack: () => void }) {
  return (
    <header className="post-header">
      <button type="button" className="post-header-back" aria-label="前の画面に戻る" onClick={onBack}>
        ‹
      </button>
      <h1 className="post-title">投稿作成</h1>
    </header>
  )
}

/** 押すと選択・入力のパネルを開く行（design の「ファッション選択 必須 未選択 ›」） */
function SelectRow({
  label,
  required = false,
  summary,
  emptyText,
  error,
  onClick,
}: {
  label: string
  required?: boolean
  summary?: string
  emptyText: string
  error?: string
  onClick: () => void
}) {
  return (
    <div className="post-row-wrap">
      <button type="button" className={`post-row${error ? ' post-row-error' : ''}`} onClick={onClick}>
        <span className="post-row-label">
          {label}{' '}
          {required ? <span className="post-required">必須</span> : <span className="post-optional">任意</span>}
        </span>
        <span className={`post-row-value${summary ? '' : ' post-row-value-empty'}`}>{summary ?? emptyText}</span>
        <span className="post-row-arrow" aria-hidden="true">
          ›
        </span>
      </button>
      {error && <p className="post-field-error post-row-message">{error}</p>}
    </div>
  )
}

function TextField({
  label,
  value,
  maxLength,
  placeholder,
  multiline = false,
  type = 'text',
  hint,
  error,
  onChange,
}: {
  label: string
  value: string
  maxLength: number
  placeholder: string
  multiline?: boolean
  type?: string
  hint?: string
  error?: string
  onChange: (value: string) => void
}) {
  const id = useId()
  const className = `post-input${multiline ? ' post-textarea' : ''}${error ? ' post-input-error' : ''}`
  return (
    <section className="post-section">
      <label htmlFor={id} className="post-label">
        {label}
      </label>
      {multiline ? (
        <textarea id={id} className={className} rows={8} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
      ) : (
        <input id={id} className={className} type={type} value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
      )}
      <Counter value={value} max={maxLength} />
      {hint && <p className="post-hint">{hint}</p>}
      {error && <p className="post-field-error">{error}</p>}
    </section>
  )
}

/** 文字数の表示。前後の空白は送信時に除くため数えない（バックエンドの数え方と同じ） */
function Counter({ value, max }: { value: string; max: number }) {
  const length = value.trim().length
  return (
    <p className={`post-length${length > max ? ' post-length-over' : ''}`}>
      {length} / {max}
    </p>
  )
}

/** タグ選択の行に出す要約（2つまでは並べ、それ以上は件数で示す） */
function summarizeTags(tags: string[]): string | undefined {
  if (tags.length === 0) return undefined
  if (tags.length <= 2) return tags.join('・')
  return `${tags.slice(0, 2).join('・')} ほか${tags.length - 2}件`
}

export default PostPage
