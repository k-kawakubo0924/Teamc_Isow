import { useCallback, useRef } from 'react'
import type { TimelineItem } from '../../api/posts'
import { setFavorite, setLike } from '../../api/reactions'

type Kind = 'like' | 'favorite'

/** 投稿1件・操作1種類（いいね／お気に入り）ごとの状態 */
type Entry = {
  /** サーバーで確定している状態（失敗したときはここに戻す） */
  confirmed: { on: boolean; likeCount: number }
  /** 利用者が最後に選んだ状態（画面にはこれを表示する） */
  desired: boolean
  /** 通信中か（通信は同時に1つだけにする） */
  inFlight: boolean
}

const FAILURE_MESSAGES: Record<Kind, { on: string; off: string }> = {
  like: { on: 'いいねできませんでした', off: 'いいねを取り消せませんでした' },
  favorite: { on: 'お気に入りに追加できませんでした', off: 'お気に入りから外せませんでした' },
}

/**
 * いいね・お気に入りを押したときの処理。
 * - 押した瞬間に画面へ反映し、裏で API を呼ぶ。成功したらサーバーが返した件数で確定する
 * - 失敗したら、サーバーで確定していた状態に戻し、onError で理由を知らせる
 * - 連打されても、通信は投稿・操作ごとに同時に1つだけにする。通信が終わった時点で利用者の最後の選択と
 *   サーバーの状態が違えば、もう1回だけ送る（最終的に最後の選択どおりになる）
 */
export function useReactions(
  token: string | null,
  updatePost: (postId: number, patch: Partial<TimelineItem>) => void,
  onError: (message: string) => void,
) {
  const entries = useRef(new Map<string, Entry>())

  /** 利用者の最後の選択を画面に反映する（件数は確定済みの件数との差で計算する） */
  const show = useCallback(
    (postId: number, kind: Kind, entry: Entry) => {
      if (kind === 'favorite') {
        updatePost(postId, { favoritedByMe: entry.desired })
        return
      }
      const diff = entry.desired === entry.confirmed.on ? 0 : entry.desired ? 1 : -1
      updatePost(postId, { likedByMe: entry.desired, likeCount: entry.confirmed.likeCount + diff })
    },
    [updatePost],
  )

  const send = useCallback(
    async (postId: number, kind: Kind, entry: Entry, authToken: string) => {
      entry.inFlight = true
      let target = entry.desired
      try {
        // 通信中に押し直された場合は、終わった時点の最後の選択でもう一度送る
        while (entry.desired !== entry.confirmed.on) {
          target = entry.desired
          if (kind === 'like') {
            const result = await setLike(postId, target, authToken)
            entry.confirmed = { on: result.liked, likeCount: result.likeCount }
          } else {
            const result = await setFavorite(postId, target, authToken)
            entry.confirmed = { ...entry.confirmed, on: result.favorited }
          }
        }
        // サーバーが返した件数（他の人のいいねも含む）で表示を確定する
        show(postId, kind, entry)
      } catch (err) {
        entry.desired = entry.confirmed.on
        show(postId, kind, entry)
        const reason = err instanceof Error ? err.message : String(err)
        onError(`${FAILURE_MESSAGES[kind][target ? 'on' : 'off']}：${reason}`)
      } finally {
        entry.inFlight = false
      }
    },
    [show, onError],
  )

  /** いいね・お気に入りのボタンを押した。post は押した時点の表示内容 */
  const toggle = useCallback(
    (post: TimelineItem, kind: Kind) => {
      if (!token) return
      const key = `${post.id}:${kind}`
      const shown = kind === 'like' ? post.likedByMe : post.favoritedByMe
      let entry = entries.current.get(key)
      if (!entry) {
        entry = { confirmed: { on: shown, likeCount: post.likeCount }, desired: shown, inFlight: false }
        entries.current.set(key, entry)
      }
      entry.desired = !shown
      show(post.id, kind, entry)
      if (!entry.inFlight) void send(post.id, kind, entry, token)
    },
    [token, show, send],
  )

  return { toggle }
}
