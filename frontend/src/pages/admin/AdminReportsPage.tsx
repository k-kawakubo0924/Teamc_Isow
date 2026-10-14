import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { listReports, type AdminReportListItem } from '../../api/admin'
import { reportReasonLabel } from '../../api/reports'
import { useAuth } from '../../auth/authContext'
import { formatAdminDateTime } from './adminFormat'
import { REPORT_STATUS_LABELS, REPORT_TARGET_LABELS } from './reportLabels'

/**
 * 通報の一覧（/admin/reports。docs/admin.md「通報」）。同じ対象への通報は1行にまとめ、通報の数と理由の内訳を出す。
 * 既定は未対応だけを、新しく通報された順に出す。DM の本文は一覧に出さない（見たことを記録する詳細でだけ出す）
 */
function AdminReportsPage() {
  const { token } = useAuth()
  const [status, setStatus] = useState('OPEN')
  const [targetType, setTargetType] = useState('')
  return (
    <>
      <h1 className="admin-page-title">通報</h1>
      <div className="admin-reports-filters">
        <label className="admin-reports-filter">
          状態
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="OPEN">未対応</option>
            <option value="RESOLVED">対応済み</option>
            <option value="DISMISSED">却下</option>
            <option value="">すべて</option>
          </select>
        </label>
        <label className="admin-reports-filter">
          対象
          <select value={targetType} onChange={(e) => setTargetType(e.target.value)}>
            <option value="">すべて</option>
            <option value="POST">投稿</option>
            <option value="USER">ユーザー</option>
            <option value="MESSAGE">DM のメッセージ</option>
          </select>
        </label>
      </div>
      {/* 絞り込みを変えたら読み込み直す */}
      {token !== null && <ReportsTable key={`${status}:${targetType}`} token={token} status={status} targetType={targetType} />}
    </>
  )
}

function ReportsTable({ token, status, targetType }: { token: string; status: string; targetType: string }) {
  const [items, setItems] = useState<AdminReportListItem[] | null>(null)
  const [nextPage, setNextPage] = useState(1)
  const [hasNext, setHasNext] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listReports({ status, targetType, page: 0 }, token)
      .then((res) => {
        if (cancelled) return
        setItems(res.items)
        setHasNext(res.hasNext)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : '読み込めませんでした。')
      })
    return () => {
      cancelled = true
    }
  }, [token, status, targetType])

  const loadMore = async () => {
    if (loading) return
    setLoading(true)
    try {
      const res = await listReports({ status, targetType, page: nextPage }, token)
      setItems((prev) => {
        const list = prev ?? []
        return [...list, ...res.items.filter((item) => !list.some((i) => i.caseId === item.caseId))]
      })
      setHasNext(res.hasNext)
      setNextPage((page) => page + 1)
    } catch (err) {
      setError(err instanceof Error ? err.message : '読み込めませんでした。')
    } finally {
      setLoading(false)
    }
  }

  if (error) {
    return (
      <p className="admin-reports-error" role="alert">
        {error}
      </p>
    )
  }
  if (items === null) {
    return <p className="admin-reports-empty">読み込み中…</p>
  }
  if (items.length === 0) {
    return <p className="admin-reports-empty">該当する通報はありません。</p>
  }
  return (
    <>
      <table className="admin-reports-table">
        <thead>
          <tr>
            <th>状態</th>
            <th>対象</th>
            <th>内容</th>
            <th>通報</th>
            <th>理由</th>
            <th>最新の通報</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.caseId}>
              <td>{REPORT_STATUS_LABELS[item.status] ?? item.status}</td>
              <td>{REPORT_TARGET_LABELS[item.targetType] ?? item.targetType}</td>
              <td>
                <Link to={`/admin/reports/${item.caseId}`} className="admin-reports-link">
                  {item.summary ?? '（証拠は保存期間を過ぎて削除済み）'}
                </Link>
              </td>
              <td>{item.reportCount}件</td>
              <td>{item.reasons.map((r) => `${reportReasonLabel(r.reason)} ${r.count}`).join('、')}</td>
              <td>
                <time dateTime={item.lastReportedAt}>{formatAdminDateTime(item.lastReportedAt)}</time>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {hasNext && (
        <button type="button" className="admin-reports-more" disabled={loading} onClick={loadMore}>
          もっと見る
        </button>
      )}
    </>
  )
}

export default AdminReportsPage
