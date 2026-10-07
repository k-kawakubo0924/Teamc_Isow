import { useMemo, type ReactNode } from 'react'
import { fetchNotificationSummary } from '../api/notifications'
import { useAutoRefresh } from '../hooks/useAutoRefresh'
import { NotificationSummaryContext } from './notificationSummaryContext'

/**
 * 通知の未読件数（ホームのベルのバッジと、通知一覧のタブのバッジ）を読み込み、共有する。
 * 取り直すタイミングは DM の件数と同じ（useAutoRefresh）
 */
export function NotificationSummaryProvider({ children }: { children: ReactNode }) {
  const { value: summary, refresh } = useAutoRefresh(fetchNotificationSummary)
  const value = useMemo(() => ({ summary, refresh }), [summary, refresh])
  return <NotificationSummaryContext.Provider value={value}>{children}</NotificationSummaryContext.Provider>
}
