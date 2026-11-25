import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation } from 'react-router'
import { useAuth } from '../auth/authContext'

/** 画面を開いている間に取り直す間隔 */
const REFRESH_INTERVAL_MS = 60 * 1000

/**
 * 件数のように、画面を開いている間に新しくしておきたい値を読み込む（DM のバッジ・通知のバッジで共通）。
 * リアルタイムに届く仕組みはまだないため、次のタイミングで取り直す（遅れても最大1分）。
 * - 画面を開いたとき・画面を移動したとき（URL が変わるたび）
 * - ブラウザのタブに戻ってきたとき
 * - 画面を開いている間は REFRESH_INTERVAL_MS ごと（タブが裏にある間は取り直さない）
 * - 件数が変わる操作の直後（各画面から refresh を呼ぶ）
 *
 * @param fetchValue 値を読み込む関数。毎回作り直される関数でもよい（最新のものを使う）
 * @returns value はまだ読み込んでいない・読み込めなかった場合は null（読めなかったときは前回の値のまま）
 */
export function useAutoRefresh<T>(fetchValue: (token: string, signal: AbortSignal) => Promise<T>) {
  const { token } = useAuth()
  const { pathname } = useLocation()
  const [value, setValue] = useState<T | null>(null)
  const controllerRef = useRef<AbortController | null>(null)
  const fetchRef = useRef(fetchValue)

  useEffect(() => {
    fetchRef.current = fetchValue
  })

  const refresh = useCallback(() => {
    if (!token) return
    // 前の読み込みが終わっていなければ取り消し、新しい結果だけを使う
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller
    fetchRef.current(token, controller.signal)
      .then((result) => {
        if (!controller.signal.aborted) setValue(result)
      })
      .catch(() => {
        // 読めなくても画面は使える。前回の値を出したままにし、次のタイミングで取り直す
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

  return { value, refresh }
}
