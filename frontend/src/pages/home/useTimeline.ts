import { useCallback, useEffect, useRef, useState } from 'react'
import { fetchTimeline, type TimelineItem, type TimelineTab } from '../../api/posts'

export type TimelineState = {
  posts: TimelineItem[]
  /** 次に読み込むページ番号（0 から） */
  nextPage: number
  hasNext: boolean
  status: 'idle' | 'loading' | 'error'
  errorMessage: string | null
}

const INITIAL_STATE: TimelineState = { posts: [], nextPage: 0, hasNext: true, status: 'idle', errorMessage: null }

/**
 * ホームの投稿一覧の読み込み（無限スクロール用）。loadMore を呼ぶたびに次の1ページを読み込み、末尾に足す。
 * - すでに表示している id の投稿は足さない（ページ番号で区切るため、途中で投稿やいいねが増えると重複しうる）
 * - 読み込み中に loadMore が呼ばれても、二重には読み込まない
 * - 失敗したら自動ではやり直さない（retry で再開する）
 * - タブごとにこの hook を使う側の部品を作り直す（key を変える）想定。画面を離れたら読み込みを中断する
 */
export function useTimeline(tab: TimelineTab, token: string | null) {
  const [state, setState] = useState<TimelineState>(INITIAL_STATE)
  const loadingRef = useRef(false)
  const controllerRef = useRef<AbortController | null>(null)

  useEffect(() => () => controllerRef.current?.abort(), [])

  const loadMore = useCallback(async () => {
    if (!token || loadingRef.current || !state.hasNext || state.status === 'error') return
    loadingRef.current = true
    const controller = new AbortController()
    controllerRef.current = controller
    setState((prev) => ({ ...prev, status: 'loading', errorMessage: null }))
    try {
      const page = await fetchTimeline(tab, state.nextPage, token, controller.signal)
      setState((prev) => {
        const seen = new Set(prev.posts.map((post) => post.id))
        const added = page.posts.filter((post) => {
          if (seen.has(post.id)) return false
          seen.add(post.id)
          return true
        })
        return {
          posts: [...prev.posts, ...added],
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
  }, [tab, token, state.hasNext, state.status, state.nextPage])

  /** 「もう一度読み込む」。エラーの状態を解除すると、一覧の末尾が見えていれば読み込みが再開する */
  const retry = useCallback(() => {
    setState((prev) => ({ ...prev, status: 'idle', errorMessage: null }))
  }, [])

  /** 表示中の1件の一部を書き換える（いいね・お気に入りを押したときの反映に使う） */
  const updatePost = useCallback((postId: number, patch: Partial<TimelineItem>) => {
    setState((prev) => ({
      ...prev,
      posts: prev.posts.map((post) => (post.id === postId ? { ...post, ...patch } : post)),
    }))
  }, [])

  return { state, loadMore, retry, updatePost }
}
