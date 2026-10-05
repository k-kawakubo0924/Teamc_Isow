import { useCallback, useEffect, useRef, useState } from 'react'

/** 1ページ分の一覧（投稿一覧・フォロー中一覧などで共通） */
export type Page<T> = {
  items: T[]
  hasNext: boolean
}

/** page（0 から）のページを読み込む関数 */
export type FetchPage<T> = (page: number, token: string, signal: AbortSignal) => Promise<Page<T>>

export type PagedListState<T> = {
  items: T[]
  /** 次に読み込むページ番号（0 から） */
  nextPage: number
  hasNext: boolean
  status: 'idle' | 'loading' | 'error'
  errorMessage: string | null
}

/**
 * ページに分かれた一覧の読み込み（無限スクロール用）。loadMore を呼ぶたびに次の1ページを読み込み、末尾に足す。
 * - すでに表示している id のものは足さない（ページ番号で区切るため、途中で件数が増えると重複しうる）
 * - 読み込み中に loadMore が呼ばれても、二重には読み込まない
 * - 失敗したら自動ではやり直さない（retry で再開する）
 * - 読み込む一覧（タブ・ユーザー・検索語など）ごとにこの hook を使う側の部品を作り直す（key を変える）想定。
 *   fetchPage は最初に渡されたものを使い続ける。画面を離れたら読み込みを中断する
 */
export function usePagedList<T extends { id: number }>(fetchPage: FetchPage<T>, token: string | null) {
  const [state, setState] = useState<PagedListState<T>>({
    items: [],
    nextPage: 0,
    hasNext: true,
    status: 'idle',
    errorMessage: null,
  })
  const loadingRef = useRef(false)
  const controllerRef = useRef<AbortController | null>(null)
  // 呼び出し側で毎回作り直される関数でも、読み込みをやり直さないよう最初のものを持っておく
  const fetchPageRef = useRef(fetchPage)

  useEffect(() => () => controllerRef.current?.abort(), [])

  const loadMore = useCallback(async () => {
    if (!token || loadingRef.current || !state.hasNext || state.status === 'error') return
    loadingRef.current = true
    const controller = new AbortController()
    controllerRef.current = controller
    setState((prev) => ({ ...prev, status: 'loading', errorMessage: null }))
    try {
      const page = await fetchPageRef.current(state.nextPage, token, controller.signal)
      setState((prev) => {
        const seen = new Set(prev.items.map((item) => item.id))
        const added = page.items.filter((item) => {
          if (seen.has(item.id)) return false
          seen.add(item.id)
          return true
        })
        return {
          items: [...prev.items, ...added],
          nextPage: prev.nextPage + 1,
          hasNext: page.hasNext,
          status: 'idle',
          errorMessage: null,
        }
      })
    } catch (err) {
      // 画面を離れた・タブを切り替えたことによる中断は、エラーとして扱わない
      if (controller.signal.aborted) return
      setState((prev) => ({
        ...prev,
        status: 'error',
        errorMessage: err instanceof Error ? err.message : String(err),
      }))
    } finally {
      loadingRef.current = false
    }
  }, [token, state.hasNext, state.status, state.nextPage])

  /** 「もう一度読み込む」。エラーの状態を解除すると、一覧の末尾が見えていれば読み込みが再開する */
  const retry = useCallback(() => {
    setState((prev) => ({ ...prev, status: 'idle', errorMessage: null }))
  }, [])

  /** 表示中の1件の一部を書き換える（いいね・フォローを押したときの反映に使う） */
  const updateItem = useCallback((id: number, patch: Partial<T>) => {
    setState((prev) => ({
      ...prev,
      items: prev.items.map((item) => (item.id === id ? { ...item, ...patch } : item)),
    }))
  }, [])

  return { state, loadMore, retry, updateItem }
}

/** 一覧の末尾がこの距離（px）まで近づいたら、次のページを読み込み始める */
const PRELOAD_MARGIN_PX = 400

/**
 * 無限スクロールの目印（一覧の末尾に置く要素）に付ける ref を返す。目印が画面に近づいたら loadMore を呼ぶ。
 * 最初の1ページもこの仕組みで読み込む。読み込みのたびに作り直し、まだ画面が埋まっていなければ続けて次を読み込む
 */
export function useLoadMoreOnScroll(loadMore: () => Promise<void>) {
  const sentinelRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const sentinel = sentinelRef.current
    if (!sentinel) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) void loadMore()
      },
      { rootMargin: `0px 0px ${PRELOAD_MARGIN_PX}px 0px` },
    )
    observer.observe(sentinel)
    return () => observer.disconnect()
  }, [loadMore])
  return sentinelRef
}
