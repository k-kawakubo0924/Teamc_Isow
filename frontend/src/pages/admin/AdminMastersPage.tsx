import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { createMaster, listMasters, setMasterActive, type AdminMasterItem, type AdminMasterKind } from '../../api/admin'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/authContext'

/** 切り替えて扱う4種類。選んでいる種類は URL（?kind=）に持ち、再読み込みしても同じ種類を開く */
const KINDS: { kind: AdminMasterKind; label: string }[] = [
  { kind: 'fashion-categories', label: 'ファッションの種類' },
  { kind: 'body-types', label: '骨格タイプ' },
  { kind: 'personal-colors', label: 'パーソナルカラー' },
  { kind: 'official-tags', label: '公式タグ' },
]

/**
 * マスタ管理（docs/admin.md「マスタの管理」）。
 * ファッションの種類・骨格タイプ・パーソナルカラー・公式タグを切り替えて、一覧・追加・無効化・有効に戻すを行う
 */
function AdminMastersPage() {
  const { token } = useAuth()
  const [params, setParams] = useSearchParams()
  const current = KINDS.find((k) => k.kind === params.get('kind')) ?? KINDS[0]

  return (
    <>
      <h1 className="admin-page-title">マスタ管理</h1>
      <div className="admin-masters-tabs" role="tablist" aria-label="マスタの種類">
        {KINDS.map((k) => (
          <button
            key={k.kind}
            type="button"
            role="tab"
            aria-selected={k.kind === current.kind}
            className={k.kind === current.kind ? 'admin-masters-tab admin-masters-tab-active' : 'admin-masters-tab'}
            onClick={() => setParams({ kind: k.kind })}
          >
            {k.label}
          </button>
        ))}
      </div>
      {/* 種類を切り替えたら、入力中の名前・メッセージ・確認の表示を引き継がないよう作り直す */}
      {token !== null && <MasterPanel key={current.kind} kind={current.kind} label={current.label} token={token} />}
    </>
  )
}

function MasterPanel({ kind, label, token }: { kind: AdminMasterKind; label: string; token: string }) {
  const [items, setItems] = useState<AdminMasterItem[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [nameError, setNameError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  // 再描画を待たずに連打を止めるため、送信中かどうかを ref にも持つ
  const submittingRef = useRef(false)
  /** 操作が成功したときのメッセージ */
  const [notice, setNotice] = useState<string | null>(null)
  /** 無効化・有効に戻すが失敗したときのエラー */
  const [rowError, setRowError] = useState<string | null>(null)
  /** 無効にする前の確認を出している行 */
  const [confirmingId, setConfirmingId] = useState<number | null>(null)
  /** 無効化・有効に戻すを送っている行（その行のボタンを押せなくする） */
  const [busyId, setBusyId] = useState<number | null>(null)

  useEffect(() => {
    let cancelled = false
    listMasters(kind, token)
      .then((list) => {
        if (!cancelled) setItems(list)
      })
      .catch((err: unknown) => {
        if (!cancelled) setLoadError(errorMessage(err))
      })
    return () => {
      cancelled = true
    }
  }, [kind, token])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (submittingRef.current || name.trim() === '') return
    submittingRef.current = true
    setSubmitting(true)
    setNameError(null)
    setNotice(null)
    setRowError(null)
    try {
      const result = await createMaster(kind, name, token)
      setItems((prev) => upsert(prev, result.item))
      setName('')
      setNotice(
        result.madeOfficial
          ? `既存のタグを公式にしました（「${result.item.name}」）`
          : `「${result.item.name}」を追加しました`,
      )
    } catch (err) {
      // 重複・文字数の超過は、サーバーが errors.name に理由を返す。入力した名前は直して送り直せるよう残す
      setNameError(err instanceof ApiError && err.body?.errors.name ? err.body.errors.name : errorMessage(err))
    } finally {
      submittingRef.current = false
      setSubmitting(false)
    }
  }

  const changeActive = async (item: AdminMasterItem, active: boolean) => {
    setBusyId(item.id)
    setNotice(null)
    setRowError(null)
    try {
      const updated = await setMasterActive(kind, item.id, active, token)
      setItems((prev) => upsert(prev, updated))
      setNotice(active ? `「${updated.name}」を有効に戻しました` : `「${updated.name}」を無効にしました`)
    } catch (err) {
      setRowError(errorMessage(err))
    } finally {
      setConfirmingId(null)
      setBusyId(null)
    }
  }

  return (
    <section className="admin-masters-panel" role="tabpanel" aria-label={label}>
      <form className="admin-masters-form" onSubmit={handleSubmit} noValidate>
        <div className="admin-masters-form-row">
          <input
            type="text"
            className="admin-masters-input"
            aria-label="追加する名前"
            placeholder={`追加する${label}の名前`}
            value={name}
            aria-invalid={nameError !== null}
            onChange={(e) => {
              setName(e.target.value)
              setNameError(null)
            }}
          />
          <button
            type="submit"
            className="admin-masters-add"
            disabled={submitting || name.trim() === ''}
          >
            追加
          </button>
        </div>
        {nameError !== null && (
          <p className="admin-masters-field-error" role="alert">
            {nameError}
          </p>
        )}
        <p className="admin-masters-hint">追加したものは一覧の末尾に並びます。</p>
      </form>

      {notice !== null && (
        <p className="admin-masters-notice" role="status">
          {notice}
        </p>
      )}
      {rowError !== null && (
        <p className="admin-masters-error" role="alert">
          {rowError}
        </p>
      )}

      {loadError !== null ? (
        <p className="admin-masters-error" role="alert">
          {loadError}
        </p>
      ) : items === null ? (
        <p className="admin-masters-empty">読み込み中…</p>
      ) : items.length === 0 ? (
        <p className="admin-masters-empty">まだ登録されていません。</p>
      ) : (
        <table className="admin-masters-table">
          <thead>
            <tr>
              <th scope="col">名前</th>
              <th scope="col">並び順</th>
              <th scope="col">状態</th>
              <th scope="col">操作</th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <MasterRow
                key={item.id}
                item={item}
                busy={busyId === item.id}
                confirming={confirmingId === item.id}
                onAskDeactivate={() => setConfirmingId(item.id)}
                onCancel={() => setConfirmingId(null)}
                onDeactivate={() => changeActive(item, false)}
                onActivate={() => changeActive(item, true)}
              />
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}

function MasterRow({
  item,
  busy,
  confirming,
  onAskDeactivate,
  onCancel,
  onDeactivate,
  onActivate,
}: {
  item: AdminMasterItem
  busy: boolean
  confirming: boolean
  onAskDeactivate: () => void
  onCancel: () => void
  onDeactivate: () => void
  onActivate: () => void
}) {
  const confirmId = `admin-masters-confirm-${item.id}`
  return (
    <>
      <tr className={item.active ? 'admin-masters-row' : 'admin-masters-row admin-masters-row-inactive'}>
        <td className="admin-masters-name">{item.name}</td>
        <td className="admin-masters-order">{item.displayOrder}</td>
        <td>
          <span className={item.active ? 'admin-masters-status' : 'admin-masters-status admin-masters-status-inactive'}>
            {item.active ? '有効' : '無効'}
          </span>
        </td>
        <td className="admin-masters-actions">
          {item.active ? (
            <button
              type="button"
              className="admin-masters-action"
              disabled={busy || confirming}
              onClick={onAskDeactivate}
            >
              無効にする
            </button>
          ) : (
            <button type="button" className="admin-masters-action" disabled={busy} onClick={onActivate}>
              有効に戻す
            </button>
          )}
        </td>
      </tr>
      {confirming && (
        <tr className="admin-masters-confirm-row">
          <td colSpan={4}>
            {/* ブラウザの確認ダイアログではなく、画面の中で確かめる（無効化は利用者側の選択肢から消えるため） */}
            <div className="admin-masters-confirm" role="alertdialog" aria-labelledby={confirmId}>
              <p id={confirmId} className="admin-masters-confirm-text">
                「{item.name}」を無効にしますか？
                <br />
                利用者は新しく選べなくなります。既存の投稿・プロフィールの表示はそのままです。あとで有効に戻せます。
              </p>
              <div className="admin-masters-confirm-buttons">
                {/* 誤って続けて押しても実行しないよう、最初は「やめる」を選んだ状態にする */}
                <button type="button" className="admin-masters-confirm-cancel" autoFocus disabled={busy} onClick={onCancel}>
                  やめる
                </button>
                <button type="button" className="admin-masters-confirm-ok" disabled={busy} onClick={onDeactivate}>
                  無効にする
                </button>
              </div>
            </div>
          </td>
        </tr>
      )}
    </>
  )
}

/** 同じ ID があれば置き換え、なければ末尾に加える（追加したもの・公式にしたタグは末尾に並ぶため） */
function upsert(items: AdminMasterItem[] | null, item: AdminMasterItem): AdminMasterItem[] {
  const list = items ?? []
  return list.some((i) => i.id === item.id) ? list.map((i) => (i.id === item.id ? item : i)) : [...list, item]
}

function errorMessage(err: unknown): string {
  return err instanceof Error ? err.message : 'エラーが発生しました。時間をおいて再度お試しください。'
}

export default AdminMastersPage
