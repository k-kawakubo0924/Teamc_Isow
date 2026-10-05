import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useLocation } from 'react-router'
import { fetchConversationSummary, type ConversationSummary } from '../api/conversations'
import { useAuth } from '../auth/authContext'
import { DmSummaryContext } from './dmSummaryContext'

/** 画面を開いている間に、DM の件数を取り直す間隔 */
const REFRESH_INTERVAL_MS = 60 * 1000

/**
 * DM の件数（下部ナビの DM のバッジと、DM一覧の上下の件数）を読み込み、共有する。
 * リアルタイムに届く仕組みはまだないため、次のタイミングで取り直す（遅れても最大1分）。
 * - 下部ナビのある画面を開いたとき・画面を移動したとき（URL が変わるたび）
 * - ブラウザのタブに戻ってきたとき
 * - 画面を開いている間は REFRESH_INTERVAL_MS ごと（タブが裏にある間は取り直さない）
 * - 承認・拒否など、件数が変わる操作の直後（各画面から refresh を呼ぶ）
 */
export function DmSummaryProvider({ children }: { children: ReactNode }) {
  const { token } = useAuth()
  const { pathname } = useLocation()
  const [summary, setSummary] = useState<ConversationSummary | null>(null)
  const controllerRef = useRef<AbortController | null>(null)

  const refresh = useCallback(() => {
    if (!token) return
    // 前の読み込みが終わっていなければ取り消し、新しい結果だけを使う
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    fetchConversationSummary(token, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setSummary(result)
      })
      .catch(() => {
        // 読めなくても画面は使える。前回の件数を出したままにし、次のタイミングで取り直す
      })
  }, [token])

  // 画面を移動するたびに取り直す（pathname は取り直すきっかけとして使う）
  useEffect(() => {
    refresh()
  }, [refresh, pathname])

  useEffect(() => {
    const refreshIfVisible = () => {
      if (document.visibilityState === 'visible') refresh()
    }
    document.addEventListener('visibilitychange', refreshIfVisible)
    const timer = setInterval(refreshIfVisible, REFRESH_INTERVAL_MS)
    return () => {
      document.removeEventListener('visibilitychange', refreshIfVisible)
      clearInterval(timer)
    }
  }, [refresh])

  useEffect(() => () => controllerRef.current?.abort(), [])

  const value = useMemo(() => ({ summary, refresh }), [summary, refresh])
  return <DmSummaryContext.Provider value={value}>{children}</DmSummaryContext.Provider>
}
