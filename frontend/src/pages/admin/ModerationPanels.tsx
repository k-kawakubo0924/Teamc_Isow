import { useId, useState, type ReactNode } from 'react'
import {
  hidePost,
  suspendUser,
  unhidePost,
  unsuspendUser,
  type ModerationHistoryItem,
  type ModerationPostState,
  type ModerationUserState,
} from '../../api/admin'
import { REPORT_REASONS } from '../../api/reports'
import { useAuth } from '../../auth/authContext'
import { formatAdminDateTime } from './adminFormat'

/*
 * 措置の操作（docs/admin.md「6. 措置」）。措置の画面（AdminModerationPage）と通報の詳細（AdminReportDetailPage）で使う。
 * 理由（と期間）を選び、画面の中の確認を通してから送る。通報の詳細から使うときは reportCaseId を渡し、
 * 措置にその通報を結びつける（選べば、同時に通報を「対応済み」にする）
 */

/** メモの長さの上限（バックエンドの ModerationAction.MAX_MEMO_LENGTH） */
const MAX_MEMO_LENGTH = 1000

/** 利用停止の期間（バックエンドの AdminModerationService.SUSPENSION_DAYS）。値が空なら無期限 */
const SUSPENSION_PERIODS: { value: string; label: string }[] = [
  { value: '1', label: '1日' },
  { value: '3', label: '3日' },
  { value: '7', label: '7日' },
  { value: '30', label: '30日' },
  { value: '', label: '無期限' },
]

const ACTION_LABELS: Record<string, string> = {
  HIDE_POST: '非公開にした',
  UNHIDE_POST: '公開に戻した',
  SUSPEND_USER: '利用停止にした',
  UNSUSPEND_USER: '利用停止を解除した',
}

/** 投稿の状態と、非公開にする・公開に戻す */
export function PostModerationPanel({
  state,
  reportCaseId = null,
  canResolve = true,
  onChange,
}: {
  state: ModerationPostState
  reportCaseId?: number | null
  /** 結びつけた通報を「対応済み」にするのを選べるか（通報が未対応のときだけ） */
  canResolve?: boolean
  onChange: (state: ModerationPostState) => void
}) {
  const { token } = useAuth()
  const headingId = useId()
  const form = useActionForm()
  const reasons = REPORT_REASONS.filter((option) => option.targets.includes('POST'))

  const run = form.runner(async () => {
    if (!token) return
    const next = state.hidden
      ? await unhidePost(state.postId, form.memo.trim(), token)
      : await hidePost(
          state.postId,
          { reason: form.reason, memo: form.memo.trim(), reportCaseId, resolveReport: reportCaseId !== null && canResolve && form.resolve },
          token,
        )
    onChange(next)
  })

  const label = state.hidden ? '公開に戻す' : '非公開にする'
  return (
    <section className="admin-moderation-panel" aria-labelledby={headingId}>
      <h3 id={headingId} className="admin-moderation-title">
        {state.title}
      </h3>
      <p className="admin-moderation-meta">
        投稿 ID {state.postId} ・ @{state.authorUsername} ・ {formatAdminDateTime(state.createdAt)} に投稿
      </p>
      <p className="admin-moderation-state">
        状態：<strong>{state.hidden ? '非公開' : '公開中'}</strong>
        {state.hidden && state.hiddenAt && (
          <>
            {' '}
            （{state.hiddenReasonLabel ?? state.hiddenReason}・{formatAdminDateTime(state.hiddenAt)} から）
          </>
        )}
      </p>
      {state.hidden && (
        <p className="admin-moderation-hint">
          非公開でも、投稿の内容と写真はサーバーに残り、投稿者本人には見えます（削除の機能はまだありません）。
        </p>
      )}

      {!state.hidden && <ReasonSelect form={form} options={reasons} />}
      <ActionFields form={form} reportCaseId={reportCaseId} withResolve={!state.hidden && canResolve} />
      <ActionButton form={form} label={label} />

      {form.confirming && (
        <ConfirmDialog
          title={state.hidden ? '投稿を公開に戻しますか？' : '投稿を非公開にしますか？'}
          okLabel={label}
          busy={form.busy}
          onCancel={form.cancel}
          onOk={run}
        >
          {state.hidden ? (
            <p>ほかの人からも、また見えるようになります。投稿者に通知が届きます。</p>
          ) : (
            <>
              <p>理由：{reasons.find((option) => option.reason === form.reason)?.label}</p>
              <p>
                ほかの人からは、存在しない投稿と同じに見えます。投稿者本人には見えます。投稿者に通知が届きます。
              </p>
            </>
          )}
        </ConfirmDialog>
      )}

      <History items={state.history} />
    </section>
  )
}

/** ユーザーの状態と、利用停止にする・解除する */
export function UserModerationPanel({
  state,
  reportCaseId = null,
  canResolve = true,
  onChange,
}: {
  state: ModerationUserState
  reportCaseId?: number | null
  /** 結びつけた通報を「対応済み」にするのを選べるか（通報が未対応のときだけ） */
  canResolve?: boolean
  onChange: (state: ModerationUserState) => void
}) {
  const { token } = useAuth()
  const headingId = useId()
  const periodId = useId()
  const form = useActionForm()
  const [period, setPeriod] = useState('1')
  const periodLabel = SUSPENSION_PERIODS.find((option) => option.value === period)?.label

  const run = form.runner(async () => {
    if (!token) return
    const next = state.suspended
      ? await unsuspendUser(state.userId, form.memo.trim(), token)
      : await suspendUser(
          state.userId,
          {
            reason: form.reason,
            days: period === '' ? null : Number(period),
            memo: form.memo.trim(),
            reportCaseId,
            resolveReport: reportCaseId !== null && canResolve && form.resolve,
          },
          token,
        )
    onChange(next)
  })

  return (
    <section className="admin-moderation-panel" aria-labelledby={headingId}>
      <h3 id={headingId} className="admin-moderation-title">
        @{state.username}
      </h3>
      <p className="admin-moderation-meta">ユーザー ID {state.userId}</p>
      <p className="admin-moderation-state">
        状態：<strong>{state.suspended ? '利用停止中' : '利用中'}</strong>
        {state.suspended && (
          <>
            {' '}
            （{state.suspensionReasonLabel ?? state.suspensionReason}・
            {state.suspendedUntil === null ? '無期限' : `${formatAdminDateTime(state.suspendedUntil)} まで`}）
          </>
        )}
      </p>

      {state.admin ? (
        <p className="admin-moderation-hint">管理者は利用停止にできません。</p>
      ) : (
        <>
          {!state.suspended && (
            <>
              <ReasonSelect form={form} options={REPORT_REASONS} />
              <label htmlFor={periodId} className="admin-moderation-label">
                期間
              </label>
              <select
                id={periodId}
                className="admin-moderation-select"
                value={period}
                onChange={(e) => setPeriod(e.target.value)}
              >
                {SUSPENSION_PERIODS.map((option) => (
                  <option key={option.label} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </>
          )}
          <ActionFields form={form} reportCaseId={reportCaseId} withResolve={!state.suspended && canResolve} />
          <ActionButton form={form} label={state.suspended ? '利用停止を解除する' : '利用停止にする'} />
        </>
      )}

      {form.confirming && (
        <ConfirmDialog
          title={state.suspended ? `@${state.username} の利用停止を解除しますか？` : `@${state.username} を利用停止にしますか？`}
          okLabel={state.suspended ? '解除する' : '利用停止にする'}
          busy={form.busy}
          onCancel={form.cancel}
          onOk={run}
        >
          {state.suspended ? (
            <p>すぐに使えるようになります。利用停止のときに終了した相談は戻りません。</p>
          ) : (
            <>
              <p>
                理由：{REPORT_REASONS.find((option) => option.reason === form.reason)?.label} ・ 期間：{periodLabel}
              </p>
              <p>
                ログイン中の端末を含め、すぐに使えなくなります。ほかの人からは、存在しないユーザーと同じに見えます。
                申請中・進行中の相談は終了します（解除しても戻りません）。
              </p>
            </>
          )}
        </ConfirmDialog>
      )}

      <History items={state.history} />
    </section>
  )
}

// ---- 部品 ----

type ActionForm = ReturnType<typeof useActionForm>

/** 理由・メモ・通報の対応済み・確認・送信中・エラーの状態 */
function useActionForm() {
  const [reason, setReason] = useState('')
  const [memo, setMemo] = useState('')
  const [resolve, setResolve] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  return {
    reason,
    setReason,
    memo,
    setMemo,
    resolve,
    setResolve,
    confirming,
    busy,
    error,
    tooLong: memo.trim().length > MAX_MEMO_LENGTH,
    /** 確認を開く。needsReason なら、理由が選ばれていることを先に確かめる */
    open(needsReason: boolean) {
      if (needsReason && reason === '') {
        setError('理由を選んでください。')
        return
      }
      setError(null)
      setConfirming(true)
    },
    cancel() {
      setConfirming(false)
    },
    /** 確認の「OK」で送る。終わったら確認を閉じ、入力を空に戻す。失敗したらサーバーの文言を出す */
    runner(action: () => Promise<void>) {
      return async () => {
        if (busy) return
        setBusy(true)
        try {
          await action()
          setReason('')
          setMemo('')
          setResolve(false)
          setError(null)
        } catch (err) {
          setError(err instanceof Error ? err.message : '措置できませんでした。')
        } finally {
          setBusy(false)
          setConfirming(false)
        }
      }
    },
  }
}

function ReasonSelect({ form, options }: { form: ActionForm; options: { reason: string; label: string }[] }) {
  const id = useId()
  return (
    <>
      <label htmlFor={id} className="admin-moderation-label">
        理由
      </label>
      <select id={id} className="admin-moderation-select" value={form.reason} onChange={(e) => form.setReason(e.target.value)}>
        <option value="">選んでください</option>
        {options.map((option) => (
          <option key={option.reason} value={option.reason}>
            {option.label}
          </option>
        ))}
      </select>
    </>
  )
}

/** メモと、通報から措置するときの「対応済みにする」 */
function ActionFields({
  form,
  reportCaseId,
  withResolve,
}: {
  form: ActionForm
  reportCaseId: number | null
  withResolve: boolean
}) {
  const memoId = useId()
  return (
    <>
      <label htmlFor={memoId} className="admin-moderation-label">
        メモ（管理者だけが見ます）
      </label>
      <textarea
        id={memoId}
        className="admin-moderation-memo"
        rows={2}
        value={form.memo}
        aria-invalid={form.tooLong}
        onChange={(e) => form.setMemo(e.target.value)}
      />
      {form.tooLong && <p className="admin-moderation-over">メモは{MAX_MEMO_LENGTH}文字以内で入力してください。</p>}
      {reportCaseId !== null && withResolve && (
        <label className="admin-moderation-check">
          <input type="checkbox" checked={form.resolve} onChange={(e) => form.setResolve(e.target.checked)} />
          この通報を「対応済み」にする
        </label>
      )}
      {form.error && (
        <p className="admin-moderation-error" role="alert">
          {form.error}
        </p>
      )}
    </>
  )
}

/** 確認を開くボタン。理由が必要なのは、非公開にする・利用停止にするとき（公開に戻す・解除では要らない） */
function ActionButton({ form, label }: { form: ActionForm; label: string }) {
  const needsReason = label === '非公開にする' || label === '利用停止にする'
  return (
    <div className="admin-moderation-buttons">
      <button
        type="button"
        className="admin-moderation-button"
        disabled={form.busy || form.tooLong}
        onClick={() => form.open(needsReason)}
      >
        {label}
      </button>
    </div>
  )
}

function ConfirmDialog({
  title,
  okLabel,
  busy,
  onCancel,
  onOk,
  children,
}: {
  title: string
  okLabel: string
  busy: boolean
  onCancel: () => void
  onOk: () => void
  children: ReactNode
}) {
  const titleId = useId()
  return (
    <div className="admin-moderation-confirm" role="dialog" aria-modal="true" aria-labelledby={titleId}>
      <p id={titleId} className="admin-moderation-confirm-title">
        {title}
      </p>
      <div className="admin-moderation-confirm-text">{children}</div>
      <div className="admin-moderation-buttons">
        <button type="button" className="admin-moderation-cancel" disabled={busy} onClick={onCancel}>
          キャンセル
        </button>
        <button type="button" className="admin-moderation-button" disabled={busy} onClick={onOk}>
          {okLabel}
        </button>
      </div>
    </div>
  )
}

/** 措置の履歴（新しい順）。メモは管理者にだけ見せる（操作ログには書かない） */
function History({ items }: { items: ModerationHistoryItem[] }) {
  if (items.length === 0) return <p className="admin-moderation-hint">措置の履歴はありません。</p>
  return (
    <ol className="admin-moderation-history">
      {items.map((item) => (
        <li key={item.id} className="admin-moderation-history-item">
          <span className="admin-moderation-history-date">{formatAdminDateTime(item.createdAt)}</span>{' '}
          {ACTION_LABELS[item.action] ?? item.action}
          {item.reasonLabel && `（${item.reasonLabel}`}
          {item.action === 'SUSPEND_USER' &&
            `・${item.suspendedUntil === null ? '無期限' : `${formatAdminDateTime(item.suspendedUntil)} まで`}`}
          {item.reasonLabel && '）'} ・ {item.adminUsername}
          {item.reportCaseId !== null && ` ・ 通報 #${item.reportCaseId}`}
          {item.memo && <p className="admin-moderation-history-memo">メモ：{item.memo}</p>}
        </li>
      ))}
    </ol>
  )
}
