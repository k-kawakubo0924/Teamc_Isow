import { createContext, useContext } from 'react'
import type { NotificationSummary } from '../api/notifications'

export type NotificationSummaryContextValue = {
  /** 通知の未読件数。まだ読み込んでいない・読み込めなかった場合は null */
  summary: NotificationSummary | null
  /** 件数を取り直す（既読・未読・削除など、件数が変わる操作の直後に呼ぶ） */
  refresh: () => void
}

export const NotificationSummaryContext = createContext<NotificationSummaryContextValue | null>(null)

/** 通知の未読件数を使う。NotificationSummaryProvider（下部ナビゲーションのある画面）の内側でのみ呼べる */
export function useNotificationSummary(): NotificationSummaryContextValue {
  const value = useContext(NotificationSummaryContext)
  if (value === null) {
    throw new Error('useNotificationSummary は NotificationSummaryProvider の内側で使ってください')
  }
  return value
}
