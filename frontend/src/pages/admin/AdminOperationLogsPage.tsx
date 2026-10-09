import { useEffect, useState } from 'react'
import { listOperationLogs, type AdminOperationLogList } from '../../api/admin'
import { useAuth } from '../../auth/authContext'
import { formatAdminDateTime } from './adminFormat'

/** 操作の種類（バックエンドの AdminAction）の表示名 */
const ACTION_LABELS: Record<string, string> = {
  USER_PROMOTED_TO_ADMIN: '管理者にした',
  MASTER_CREATED: '追加した',
  TAG_MADE_OFFICIAL: '手入力のタグを公式タグにした',
  MASTER_DEACTIVATED: '無効にした',
  MASTER_ACTIVATED: '有効に戻した',
  ANNOUNCEMENT_PUBLISHED: 'お知らせの発行',
}

/** 対象の種類（バックエンドの AdminTargetType）の表示名 */
const TARGET_LABELS: Record<string, string> = {
  USER: 'ユーザー',
  FASHION_CATEGORY: 'ファッションの種類',
  BODY_TYPE: '骨格タイプ',
  PERSONAL_COLOR: 'パーソナルカラー',
  TAG: 'タグ',
  ANNOUNCEMENT: 'お知らせ',
}

/**
 * 操作ログ（docs/admin.md「管理操作のログ」）。誰がいつ何をしたかを新しい順に、ページで区切って出す。
 * 操作の種類・対象の種類は日本語で出す。後の段階で種類が増えて表示名がまだないときは、表示が消えないよう定数名のまま出す
 */
function AdminOperationLogsPage() {
  const { token } = useAuth()
  return (
    <>
      <h1 className="admin-page-title">操作ログ</h1>
      {token !== null && <OperationLogsPanel token={token} />}
    </>
  )
}

function OperationLogsPanel({ token }: { token: string }) {
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<AdminOperationLogList | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listOperationLogs(page, token)
      .then((res) => {
        if (!cancelled) setResult(res)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : '読み込みに失敗しました。')
      })
    return () => {
      cancelled = true
    }
  }, [page, token])

  const goTo = (next: number) => {
    setError(null)
    setPage(next)
  }

  // 読み込み中は、前のページの内容を出したままにする（表が消えて画面が跳ねないように）。
  // 読み込みに失敗したときは、ページを移り直せるよう読み込み中として扱わない
  const loading = error === null && (result === null || result.page !== page)

  return (
    <div className="admin-logs">
      {error !== null && (
        <p className="admin-logs-error" role="alert">
          {error}
        </p>
      )}
      {result === null ? (
        error === null && <p className="admin-logs-empty">読み込み中…</p>
      ) : result.logs.length === 0 && result.page === 0 ? (
        <p className="admin-logs-empty">操作ログはまだありません。</p>
      ) : (
        <>
          <table className="admin-logs-table" aria-busy={loading}>
            <thead>
              <tr>
                <th scope="col">日時</th>
                <th scope="col">操作した人</th>
                <th scope="col">操作の種類</th>
                <th scope="col">対象</th>
                <th scope="col">内容</th>
              </tr>
            </thead>
            <tbody>
              {result.logs.map((log) => (
                <tr key={log.id} className={log.system ? 'admin-logs-row admin-logs-row-system' : 'admin-logs-row'}>
                  <td className="admin-logs-date">
                    <time dateTime={log.operatedAt}>{formatAdminDateTime(log.operatedAt, true)}</time>
                  </td>
                  <td>
                    {log.system ? (
                      <>
                        <span className="admin-logs-system">システム</span>
                        <span className="admin-logs-system-note">（自動）</span>
                      </>
                    ) : (
                      log.operatorUsername
                    )}
                  </td>
                  <td>{ACTION_LABELS[log.action] ?? log.action}</td>
                  <td>{`${TARGET_LABELS[log.targetType] ?? log.targetType}（ID: ${log.targetId}）`}</td>
                  <td className="admin-logs-detail">{log.detail ?? ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="admin-logs-pager">
            <button
              type="button"
              className="admin-logs-pager-button"
              disabled={loading || page === 0}
              onClick={() => goTo(page - 1)}
            >
              前へ
            </button>
            <span className="admin-logs-page">{`${page + 1}ページ目`}</span>
            <button
              type="button"
              className="admin-logs-pager-button"
              disabled={loading || !result.hasNext}
              onClick={() => goTo(page + 1)}
            >
              次へ
            </button>
          </div>
        </>
      )}
    </div>
  )
}

export default AdminOperationLogsPage
