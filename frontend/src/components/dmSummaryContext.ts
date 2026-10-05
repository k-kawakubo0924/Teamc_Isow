import { createContext, useContext } from 'react'
import type { ConversationSummary } from '../api/conversations'

export type DmSummaryContextValue = {
  /** DM の件数。まだ読み込んでいない・読み込めなかった場合は null */
  summary: ConversationSummary | null
  /** 件数を取り直す（承認・拒否など、件数が変わる操作の直後に呼ぶ） */
  refresh: () => void
}

export const DmSummaryContext = createContext<DmSummaryContextValue | null>(null)

/** DM の件数を使う。DmSummaryProvider（下部ナビゲーションのある画面）の内側でのみ呼べる */
export function useDmSummary(): DmSummaryContextValue {
  const value = useContext(DmSummaryContext)
  if (value === null) {
    throw new Error('useDmSummary は DmSummaryProvider の内側で使ってください')
  }
  return value
}

/** 下部ナビの DM のバッジに出す件数（やり取り中の未読メッセージ ＋ 受け取った申請） */
export function dmBadgeCount(summary: ConversationSummary | null): number {
  return summary === null ? 0 : summary.unreadMessageCount + summary.receivedRequestCount
}
