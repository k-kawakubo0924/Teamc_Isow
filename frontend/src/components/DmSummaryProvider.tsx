import { useMemo, type ReactNode } from 'react'
import { fetchConversationSummary } from '../api/conversations'
import { useAutoRefresh } from '../hooks/useAutoRefresh'
import { DmSummaryContext } from './dmSummaryContext'

/**
 * DM の件数（下部ナビの DM のバッジと、DM一覧の上下の件数）を読み込み、共有する。
 * 取り直すタイミングは useAutoRefresh（画面の移動・タブに戻ったとき・1分ごと・件数が変わる操作の直後）
 */
export function DmSummaryProvider({ children }: { children: ReactNode }) {
  const { value: summary, refresh } = useAutoRefresh(fetchConversationSummary)
  const value = useMemo(() => ({ summary, refresh }), [summary, refresh])
  return <DmSummaryContext.Provider value={value}>{children}</DmSummaryContext.Provider>
}
