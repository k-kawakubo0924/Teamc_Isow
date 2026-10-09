import { useEffect, useRef, useState, type FormEvent } from 'react'
import { listAnnouncements, publishAnnouncement, type AdminAnnouncement } from '../../api/admin'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/authContext'
import { formatAdminDateTime } from './adminFormat'

/** 題名・本文の文字数の上限（サーバーと同じ。docs/admin.md「お知らせの発行」） */
const MAX_TITLE_LENGTH = 100
const MAX_BODY_LENGTH = 2000

type FieldErrors = { title?: string; body?: string }

/**
 * お知らせ（docs/admin.md「お知らせの発行」）。題名と本文を入れて発行し、発行済みのものを新しい順に一覧する。
 * 発行後の訂正・取り消しはないため、発行の前に画面の中で確かめる
 */
function AdminAnnouncementsPage() {
  const { token } = useAuth()
  return (
    <>
      <h1 className="admin-page-title">お知らせ</h1>
      {token !== null && <AnnouncementsPanel token={token} />}
    </>
  )
}

function AnnouncementsPanel({ token }: { token: string }) {
  const [items, setItems] = useState<AdminAnnouncement[] | null>(null)
  const [nextPage, setNextPage] = useState(1)
  const [hasNext, setHasNext] = useState(false)
  const [loadingMore, setLoadingMore] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [confirming, setConfirming] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  // 再描画を待たずに連打を止めるため、送信中かどうかを ref にも持つ
  const submittingRef = useRef(false)
  const [notice, setNotice] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listAnnouncements(0, token)
      .then((res) => {
        if (cancelled) return
        setItems(res.items)
        setHasNext(res.hasNext)
      })
      .catch((err: unknown) => {
        if (!cancelled) setLoadError(errorMessage(err))
      })
    return () => {
      cancelled = true
    }
  }, [token])

  const loadMore = async () => {
    if (loadingMore) return
    setLoadingMore(true)
    try {
      const res = await listAnnouncements(nextPage, token)
      // 読み込んだ後に発行したものがあると同じお知らせが次のページにも来るため、すでにあるものは足さない
      setItems((prev) => {
        const list = prev ?? []
        return [...list, ...res.items.filter((item) => !list.some((i) => i.id === item.id))]
      })
      setHasNext(res.hasNext)
      setNextPage((page) => page + 1)
    } catch (err) {
      setLoadError(errorMessage(err))
    } finally {
      setLoadingMore(false)
    }
  }

  const trimmedTitle = title.trim()
  const trimmedBody = body.trim()

  // 確認を出す前に文字数を確かめる（サーバーでも同じ確認をする）
  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    const errors: FieldErrors = {}
    if (trimmedTitle.length > MAX_TITLE_LENGTH) errors.title = `題名は${MAX_TITLE_LENGTH}文字以内で入力してください`
    if (trimmedBody.length > MAX_BODY_LENGTH) errors.body = `本文は${MAX_BODY_LENGTH}文字以内で入力してください`
    setFieldErrors(errors)
    setNotice(null)
    setSubmitError(null)
    if (Object.keys(errors).length === 0) setConfirming(true)
  }

  const publish = async () => {
    if (submittingRef.current) return
    submittingRef.current = true
    setSubmitting(true)
    try {
      const created = await publishAnnouncement(trimmedTitle, trimmedBody, token)
      setItems((prev) => [created, ...(prev ?? [])])
      setTitle('')
      setBody('')
      setNotice('お知らせを発行しました')
    } catch (err) {
      const errors = err instanceof ApiError ? err.body?.errors : undefined
      if (errors?.title || errors?.body) {
        setFieldErrors({ title: errors.title, body: errors.body })
      } else {
        setSubmitError(errorMessage(err))
      }
    } finally {
      submittingRef.current = false
      setSubmitting(false)
      setConfirming(false)
    }
  }

  return (
    <div className="admin-announcements">
      <form className="admin-announcements-form" onSubmit={handleSubmit} noValidate>
        <label className="admin-announcements-label" htmlFor="admin-announcements-title">
          題名
        </label>
        <input
          id="admin-announcements-title"
          type="text"
          className="admin-announcements-input"
          value={title}
          aria-invalid={fieldErrors.title !== undefined}
          disabled={confirming || submitting}
          onChange={(e) => {
            setTitle(e.target.value)
            setFieldErrors((prev) => ({ ...prev, title: undefined }))
          }}
        />
        <FieldFooter error={fieldErrors.title} length={trimmedTitle.length} max={MAX_TITLE_LENGTH} />

        <label className="admin-announcements-label" htmlFor="admin-announcements-body">
          本文
        </label>
        <textarea
          id="admin-announcements-body"
          className="admin-announcements-textarea"
          rows={8}
          value={body}
          aria-invalid={fieldErrors.body !== undefined}
          disabled={confirming || submitting}
          onChange={(e) => {
            setBody(e.target.value)
            setFieldErrors((prev) => ({ ...prev, body: undefined }))
          }}
        />
        <FieldFooter error={fieldErrors.body} length={trimmedBody.length} max={MAX_BODY_LENGTH} />
        <p className="admin-announcements-hint">本文は文字だけです（改行はそのまま表示されます）。</p>

        {!confirming && (
          <button
            type="submit"
            className="admin-announcements-submit"
            disabled={trimmedTitle === '' || trimmedBody === '' || submitting}
          >
            発行する
          </button>
        )}
      </form>

      {confirming && (
        // ブラウザの確認ダイアログではなく、画面の中で確かめる（発行後の訂正・取り消しはないため）
        <div className="admin-announcements-confirm" role="alertdialog" aria-labelledby="admin-announcements-confirm-title">
          <p id="admin-announcements-confirm-title" className="admin-announcements-confirm-title">
            このお知らせを発行しますか？
          </p>
          <div className="admin-announcements-preview">
            <p className="admin-announcements-preview-title">{trimmedTitle}</p>
            <p className="admin-announcements-body">{trimmedBody}</p>
          </div>
          <p className="admin-announcements-confirm-text">
            発行したお知らせは、あとから訂正・取り消しはできません。
            今は利用者側にお知らせを表示する画面はありませんが、利用者側の表示ができたあとは、発行したお知らせはすべての利用者に表示され、取り消せなくなります。
          </p>
          <div className="admin-announcements-confirm-buttons">
            {/* 誤って続けて押しても発行しないよう、最初は「戻って直す」を選んだ状態にする */}
            <button
              type="button"
              className="admin-announcements-confirm-cancel"
              autoFocus
              disabled={submitting}
              onClick={() => setConfirming(false)}
            >
              戻って直す
            </button>
            <button type="button" className="admin-announcements-confirm-ok" disabled={submitting} onClick={publish}>
              {submitting ? '発行中…' : '発行する'}
            </button>
          </div>
        </div>
      )}

      {notice !== null && (
        <p className="admin-announcements-notice" role="status">
          {notice}
        </p>
      )}
      {submitError !== null && (
        <p className="admin-announcements-error" role="alert">
          {submitError}
        </p>
      )}

      <h2 className="admin-announcements-heading">発行済みのお知らせ</h2>
      {loadError !== null && (
        <p className="admin-announcements-error" role="alert">
          {loadError}
        </p>
      )}
      {items === null ? (
        loadError === null && <p className="admin-announcements-empty">読み込み中…</p>
      ) : items.length === 0 ? (
        <p className="admin-announcements-empty">まだお知らせを発行していません。</p>
      ) : (
        <div className="admin-announcements-list">
          {items.map((item) => (
            <article key={item.id} className="admin-announcements-item">
              <h3 className="admin-announcements-item-title">{item.title}</h3>
              <p className="admin-announcements-meta">
                <time dateTime={item.publishedAt}>{formatAdminDateTime(item.publishedAt)}</time>
                <span>発行：{item.publisherUsername}</span>
              </p>
              {/* 文字のまま出す（HTML として扱わない）。改行は CSS（white-space: pre-wrap）でそのまま表示する */}
              <p className="admin-announcements-body">{item.body}</p>
            </article>
          ))}
          {hasNext && (
            <button type="button" className="admin-announcements-more" disabled={loadingMore} onClick={loadMore}>
              もっと見る
            </button>
          )}
        </div>
      )}
    </div>
  )
}

/** 欄の下のエラーと文字数 */
function FieldFooter({ error, length, max }: { error?: string; length: number; max: number }) {
  return (
    <div className="admin-announcements-field-footer">
      {error !== undefined ? (
        <p className="admin-announcements-field-error" role="alert">
          {error}
        </p>
      ) : (
        <span />
      )}
      <span className={length > max ? 'admin-announcements-count admin-announcements-count-over' : 'admin-announcements-count'}>
        {`${length} / ${max}`}
      </span>
    </div>
  )
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'エラーが発生しました。時間をおいて再度お試しください。'
}

export default AdminAnnouncementsPage
