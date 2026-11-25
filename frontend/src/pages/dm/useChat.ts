import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError } from '../../api/client'
import { fetchConversation, fetchMessages, type ConversationDetail, type Message } from '../../api/conversations'

/** 画面を開いている間に、新しいメッセージと会話の状態を取り直す間隔 */
export const POLL_INTERVAL_MS = 5 * 1000

/** 1回の取り直しで続けて読む回数の上限（新しいメッセージが一度に大量に届いた場合に、読み続けないため） */
const MAX_POLL_PAGES = 5

/**
 * チャット画面のデータ（会話の状態とメッセージ）。
 *
 * 新しいメッセージは、リアルタイムに届く仕組みがまだないため、次のように取り直す（届くまで最大で POLL_INTERVAL_MS）。
 * - 画面を開いている間は POLL_INTERVAL_MS ごとに、最後のメッセージより新しいもの（after）と会話の状態を読む
 * - タブが裏にある間は止め、戻ってきたらすぐに読む
 * - 終了・拒否した会話は新しいメッセージが来ないため、取り直さない
 * 通知の仕組み（WebSocket など）ができたら、poll を呼ぶきっかけをそれに置き換える
 */
export function useChat(id: number, token: string | null) {
  const [detail, setDetail] = useState<ConversationDetail | null>(null)
  const [messages, setMessages] = useState<Message[]>([])
  const [hasOlder, setHasOlder] = useState(false)
  const [loadingOlder, setLoadingOlder] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  // 最新のメッセージの ID（取り直しの after に使う）。読み込んだ一覧から毎回求める
  const lastIdRef = useRef<number | null>(null)
  const pollingRef = useRef(false)
  // 画面を開いている間の読み込みに使う。画面を離れたら取り消す
  const controllerRef = useRef<AbortController | null>(null)

  // 開いたときに作り、離れたら読み込み中のものをすべて取り消す。
  // 開発中は StrictMode で「開く → 離れる → 開く」が実行されるため、取り消し済みのものを使い回さないよう毎回作る
  useEffect(() => {
    const controller = new AbortController()
    controllerRef.current = controller
    return () => controller.abort()
  }, [])

  useEffect(() => {
    lastIdRef.current = messages.length > 0 ? messages[messages.length - 1].id : null
  }, [messages])

  /** 一覧に足す。送信の応答と取り直しで同じメッセージが届くことがあるため、ID で重複を省き、ID 順に並べる */
  const append = useCallback((incoming: Message[]) => {
    if (incoming.length === 0) return
    setMessages((prev) => {
      const seen = new Set(prev.map((m) => m.id))
      const added = incoming.filter((m) => !seen.has(m.id))
      return added.length === 0 ? prev : [...prev, ...added].sort((a, b) => a.id - b.id)
    })
  }, [])

  // 最初の読み込み：会話の状態と、最新のメッセージ
  useEffect(() => {
    if (!token) return
    const controller = new AbortController()
    const { signal } = controller
    Promise.all([fetchConversation(id, token, signal), fetchMessages(id, {}, token, signal)])
      .then(([conversation, page]) => {
        setDetail(conversation)
        setMessages(page.messages)
        setHasOlder(page.hasMore)
      })
      .catch((err: unknown) => {
        if (signal.aborted) return
        setLoadError(err instanceof ApiError ? err.message : '会話を読み込めませんでした。')
      })
    return () => controller.abort()
  }, [id, token])

  /** さらに古いメッセージを読み、先頭に足す（上端までスクロールしたとき） */
  const loadOlder = useCallback(async () => {
    const controller = controllerRef.current
    if (!token || !controller || loadingOlder || !hasOlder || messages.length === 0) return
    setLoadingOlder(true)
    try {
      const page = await fetchMessages(id, { before: messages[0].id }, token, controller.signal)
      setMessages((prev) => {
        const seen = new Set(prev.map((m) => m.id))
        return [...page.messages.filter((m) => !seen.has(m.id)), ...prev]
      })
      setHasOlder(page.hasMore)
    } catch {
      // 読めなくても表示中のメッセージはそのまま使える。次に上端までスクロールしたときにやり直す
    } finally {
      setLoadingOlder(false)
    }
  }, [id, token, loadingOlder, hasOlder, messages])

  /** 新しいメッセージと会話の状態を取り直す。失敗しても次の機会にやり直すため、エラーは出さない */
  const poll = useCallback(async () => {
    const controller = controllerRef.current
    if (!token || !controller || pollingRef.current) return
    pollingRef.current = true
    const { signal } = controller
    try {
      const readNewMessages = async () => {
        let after = lastIdRef.current ?? 0
        for (let i = 0; i < MAX_POLL_PAGES; i++) {
          const page = await fetchMessages(id, { after }, token, signal)
          append(page.messages)
          if (!page.hasMore || page.messages.length === 0) return
          after = page.messages[page.messages.length - 1].id
        }
      }
      const [conversation] = await Promise.all([fetchConversation(id, token, signal), readNewMessages()])
      if (!signal.aborted) setDetail(conversation)
    } catch {
      // 通信できなくても次の取り直しでやり直す
    } finally {
      pollingRef.current = false
    }
  }, [id, token, append])

  // 申請中・進行中の会話だけ、開いている間は取り直す
  const status = detail?.status
  useEffect(() => {
    if (status !== 'REQUESTED' && status !== 'ACTIVE') return
    const pollIfVisible = () => {
      if (document.visibilityState === 'visible') void poll()
    }
    document.addEventListener('visibilitychange', pollIfVisible)
    const timer = setInterval(pollIfVisible, POLL_INTERVAL_MS)
    return () => {
      document.removeEventListener('visibilitychange', pollIfVisible)
      clearInterval(timer)
    }
  }, [status, poll])

  return { detail, setDetail, messages, append, hasOlder, loadingOlder, loadOlder, loadError, poll }
}
