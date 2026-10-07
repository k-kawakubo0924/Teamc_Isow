import { useState } from 'react'
import { ApiError } from '../../api/client'
import { fetchConsultationStatuses, type ConsultationStatus } from '../../api/consultations'
import { setFollow } from '../../api/follows'
import type { UserRowItem } from './UserRow'

/**
 * ユーザーの一覧の「相談する」ボタンの状態（ユーザー ID → 状態）。フォロー中一覧・ユーザーの検索結果で共通。
 * 1ページ読むごとに、そのページの人数分を loadConsultStatuses でまとめて読む。
 * 一覧の読み込み（usePagedList）から呼ぶため、usePagedList より前に呼ぶこと
 */
export function useConsultStatuses() {
  const [consultStatuses, setConsultStatuses] = useState<Map<number, ConsultationStatus>>(new Map())

  const loadConsultStatuses = (userIds: number[], authToken: string, signal?: AbortSignal) => {
    if (userIds.length === 0) return
    fetchConsultationStatuses(userIds, authToken, signal)
      .then((statuses) => setConsultStatuses((prev) => new Map([...prev, ...statuses])))
      .catch(() => {
        // 読めなかった人のボタンは押せないままにする（フォローなど他の操作は使える）
      })
  }

  return { consultStatuses, loadConsultStatuses }
}

/**
 * ユーザーの一覧の「フォロー」ボタンの処理（フォロー中一覧・ユーザーの検索結果で共通）。
 * 通信中は同じ人のボタンを押せないようにし、結果はサーバーの応答で確定する。
 *
 * @param updateItem 一覧の1件を書き換える（usePagedList の updateItem）
 * @param onFollowChange フォローの状態が変わったときに呼ぶ（件数の表示を変える場合など）
 */
export function useFollowToggle(
  token: string | null,
  updateItem: (id: number, patch: Partial<UserRowItem>) => void,
  onFollowChange?: (following: boolean) => void,
) {
  const [pending, setPending] = useState<Set<number>>(new Set())
  const [error, setError] = useState<string | null>(null)

  const handleFollow = async (user: UserRowItem) => {
    if (!token || pending.has(user.id)) return
    setPending((prev) => new Set(prev).add(user.id))
    setError(null)
    try {
      const result = await setFollow(user.id, !user.followingByMe, token)
      updateItem(user.id, { followingByMe: result.following })
      if (result.following !== user.followingByMe) onFollowChange?.(result.following)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '操作できませんでした。時間をおいて再度お試しください。')
    } finally {
      setPending((prev) => {
        const next = new Set(prev)
        next.delete(user.id)
        return next
      })
    }
  }

  return { pending, error, handleFollow }
}
