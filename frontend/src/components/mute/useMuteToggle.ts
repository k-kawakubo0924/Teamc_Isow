import { useEffect, useState } from 'react'
import { muteUser, unmuteUser } from '../../api/mutes'
import { muteNoticeText } from './muteText'

/** 知らせを出しておく時間（ミリ秒） */
const NOTICE_MS = 5000

/**
 * ミュート（投稿の非表示）の切り替え（docs/profile.md「ミュート（投稿の非表示）」）。
 * 確認のダイアログは出さない（何も壊さず、いつでも解除できる軽い手段のため。ブロックと同じ重さで扱わない）。
 * 切り替えたら、実行したことを短く知らせる（notice。ミュートしても見ているものは消えないため）。
 * 失敗したら error を出し、状態は変えない
 */
export function useMuteToggle({
  userId,
  username,
  initialMuted,
  token,
  onChange,
}: {
  userId: number
  username: string
  initialMuted: boolean
  token: string | null
  onChange?: (muted: boolean) => void
}) {
  const [muted, setMuted] = useState(initialMuted)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  // 別の相手・読み込み直しで初めの状態が変わったら合わせる
  useEffect(() => {
    setMuted(initialMuted)
  }, [initialMuted, userId])

  useEffect(() => {
    if (notice === null) return
    const timer = setTimeout(() => setNotice(null), NOTICE_MS)
    return () => clearTimeout(timer)
  }, [notice])

  const toggle = async () => {
    if (!token || busy) return
    const next = !muted
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await (next ? muteUser(userId, token) : unmuteUser(userId, token))
      setMuted(next)
      setNotice(muteNoticeText(username, next))
      onChange?.(next)
    } catch (err) {
      setError(err instanceof Error ? err.message : '変更できませんでした。時間をおいて再度お試しください。')
    } finally {
      setBusy(false)
    }
  }

  return { muted, busy, error, notice, toggle }
}
