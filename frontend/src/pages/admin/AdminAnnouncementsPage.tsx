import { useEffect, useRef, useState, type FormEvent } from 'react'
import { listAnnouncements, publishAnnouncement, withdrawAnnouncement, type AdminAnnouncement } from '../../api/admin'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/authContext'
import { formatAdminDateTime } from './adminFormat'

/** 題名・本文の文字数の上限（サーバーと同じ。docs/admin.md「お知らせの発行」） */
const MAX_TITLE_LENGTH = 100
const MAX_BODY_LENGTH = 2000

type FieldErrors = { title?: string; body?: string }

/**
 * お知らせ（docs/admin.md「お知らせの発行」）。題名と本文を入れて発行し、発行済みのものを新しい順に一覧する。
 * 発行するとすぐに利用者に表示され、訂正はできないため、発行の前に画面の中で確かめる。
 * 誤りがあったときは一覧から取り消す（行は残り「取り消し済み」になる）。取り消しも画面の中で確かめる
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

  /** 取り消しを確かめているお知らせの ID */
  const [withdrawingId, setWithdrawingId] = useState<number | null>(null)
  const [withdrawSubmitting, setWithdrawSubmitting] = useState(false)
  const withdrawSubmittingRef = useRef(false)

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

  /** 確認で「取り消す」を押した。取り消した後の1件で一覧を置き換える（一覧は読み直さない） */
  const withdraw = async (id: number) => {
    if (withdrawSubmittingRef.current) return
    withdrawSubmittingRef.current = true
    setWithdrawSubmitting(true)
    setNotice(null)
    setSubmitError(null)
    try {
      const withdrawn = await withdrawAnnouncement(id, token)
      setItems((prev) => (prev ?? []).map((item) => (item.id === id ? withdrawn : item)))
      setNotice('お知らせを取り消しました')
    } catch (err) {
      // ほかの管理者が先に取り消していた（409）など。サーバーの理由をそのまま出す
      setSubmitError(errorMessage(err))
    } finally {
      withdrawSubmittingRef.current = false
      setWithdrawSubmitting(false)
      setWithdrawingId(null)
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
        // ブラウザの確認ダイアログではなく、画面の中で確かめる（発行後の訂正はできないため）
        <div className="admin-announcements-confirm" role="alertdialog" aria-labelledby="admin-announcements-confirm-title">
          <p id="admin-announcements-confirm-title" className="admin-announcements-confirm-title">
            このお知らせを発行しますか？
          </p>
          <div className="admin-announcements-preview">
            <p className="admin-announcements-preview-title">{trimmedTitle}</p>
            <p className="admin-announcements-body">{trimmedBody}</p>
          </div>
          <p className="admin-announcements-confirm-text">
            発行するとすぐにすべての利用者に表示されます。発行したお知らせは、あとから訂正はできません。
            誤りがあったときは、取り消してから正しい内容で発行し直してください。
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
            <article
              key={item.id}
              className={`admin-announcements-item${item.withdrawnAt ? ' admin-announcements-item-withdrawn' : ''}`}
            >
              <h3 className="admin-announcements-item-title">{item.title}</h3>
              <p className="admin-announcements-meta">
                <time dateTime={item.publishedAt}>{formatAdminDateTime(item.publishedAt)}</time>
                <span>発行：{item.publisherUsername}</span>
              </p>
              {item.withdrawnAt ? (
                <p className="admin-announcements-withdrawn">
                  取り消し済み（<time dateTime={item.withdrawnAt}>{formatAdminDateTime(item.withdrawnAt)}</time>
                  ・取り消し：{item.withdrawnByUsername}）
                </p>
              ) : withdrawingId === item.id ? (
                <WithdrawConfirm
                  item={item}
                  submitting={withdrawSubmitting}
                  onCancel={() => setWithdrawingId(null)}
                  onWithdraw={() => withdraw(item.id)}
                />
              ) : (
                <button
                  type="button"
                  className="admin-announcements-withdraw"
                  disabled={withdrawSubmitting}
                  onClick={() => {
                    setNotice(null)
                    setSubmitError(null)
                    setWithdrawingId(item.id)
                  }}
                >
                  取り消す
                </button>
              )}
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

/** 取り消しの確認（ブラウザの確認ダイアログではなく、画面の中で確かめる） */
function WithdrawConfirm({
  item,
  submitting,
  onCancel,
  onWithdraw,
}: {
  item: AdminAnnouncement
  submitting: boolean
  onCancel: () => void
  onWithdraw: () => void
}) {
  const titleId = `admin-announcements-withdraw-title-${item.id}`
  return (
    <div className="admin-announcements-confirm" role="alertdialog" aria-labelledby={titleId}>
      <p id={titleId} className="admin-announcements-confirm-title">
        「{item.title}」を取り消しますか？
      </p>
      <p className="admin-announcements-confirm-text">
        取り消すと、利用者の画面から消えます（通知一覧の「お知らせ」タブに出なくなり、未読の件数にも数えません）。
        取り消したお知らせは元に戻せません。この一覧には「取り消し済み」として残ります。
      </p>
      <div className="admin-announcements-confirm-buttons">
        {/* 誤って続けて押しても取り消さないよう、最初は「やめる」を選んだ状態にする */}
        <button type="button" className="admin-announcements-confirm-cancel" autoFocus disabled={submitting} onClick={onCancel}>
          やめる
        </button>
        <button type="button" className="admin-announcements-confirm-ok" disabled={submitting} onClick={onWithdraw}>
          {submitting ? '取り消し中…' : '取り消す'}
        </button>
      </div>
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
