import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { listContacts, type AdminContactListItem } from '../../api/admin'
import { CONTACT_CATEGORIES, contactCategoryLabel } from '../../api/contacts'
import { useAuth } from '../../auth/authContext'
import { formatAdminDateTime } from './adminFormat'
import { CONTACT_CHANNEL_LABELS, CONTACT_STATUS_LABELS } from './contactLabels'

/**
 * お問い合わせの一覧（/admin/contacts。docs/admin.md「7. お問い合わせ」）。既定は未対応だけを新しい順に出す。
 * 内容・メールアドレスは一覧に出さない（個人情報が書かれうるため、開いた詳細でだけ出す）
 */
function AdminContactsPage() {
  const { token } = useAuth()
  const [status, setStatus] = useState('OPEN')
  const [category, setCategory] = useState('')
  const [channel, setChannel] = useState('')
  return (
    <>
      <h1 className="admin-page-title">お問い合わせ</h1>
      <div className="admin-reports-filters">
        <label className="admin-reports-filter">
          状態
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            <option value="OPEN">未対応</option>
            <option value="RESOLVED">対応済み</option>
            <option value="DISMISSED">対応不要</option>
            <option value="">すべて</option>
          </select>
        </label>
        <label className="admin-reports-filter">
          種類
          <select value={category} onChange={(e) => setCategory(e.target.value)}>
            <option value="">すべて</option>
            {CONTACT_CATEGORIES.map((option) => (
              <option key={option.category} value={option.category}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label className="admin-reports-filter">
          受付
          <select value={channel} onChange={(e) => setChannel(e.target.value)}>
            <option value="">すべて</option>
            <option value="NORMAL">通常</option>
            <option value="SUSPENDED_APPEAL">利用停止中の申し立て</option>
          </select>
        </label>
      </div>
      {/* 絞り込みを変えたら読み込み直す */}
      {token !== null && (
        <ContactsTable
          key={`${status}:${category}:${channel}`}
          token={token}
          status={status}
          category={category}
          channel={channel}
        />
      )}
    </>
  )
}

function ContactsTable({
  token,
  status,
  category,
  channel,
}: {
  token: string
  status: string
  category: string
  channel: string
}) {
  const [items, setItems] = useState<AdminContactListItem[] | null>(null)
  const [nextPage, setNextPage] = useState(1)
  const [hasNext, setHasNext] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listContacts({ status, category, channel, page: 0 }, token)
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
  }, [token, status, category, channel])

  const loadMore = async () => {
    if (loading) return
    setLoading(true)
    try {
      const res = await listContacts({ status, category, channel, page: nextPage }, token)
      setItems((prev) => {
        const list = prev ?? []
        return [...list, ...res.items.filter((item) => !list.some((i) => i.id === item.id))]
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
    return <p className="admin-reports-empty">該当するお問い合わせはありません。</p>
  }
  return (
    <>
      <table className="admin-reports-table">
        <thead>
          <tr>
            <th>状態</th>
            <th>受付</th>
            <th>種類</th>
            <th>送った人</th>
            <th>受付日時</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id}>
              <td>{CONTACT_STATUS_LABELS[item.status] ?? item.status}</td>
              <td>{CONTACT_CHANNEL_LABELS[item.channel] ?? item.channel}</td>
              <td>{contactCategoryLabel(item.category)}</td>
              <td>
                <Link to={`/admin/contacts/${item.id}`} className="admin-reports-link">
                  {item.username === null ? '（退会したユーザー）' : `@${item.username}`}
                </Link>
              </td>
              <td>
                <time dateTime={item.createdAt}>{formatAdminDateTime(item.createdAt)}</time>
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

export default AdminContactsPage
