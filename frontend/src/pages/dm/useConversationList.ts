import { fetchConversations, type ConversationFilter, type ConversationListItem } from '../../api/conversations'
import { useAuth } from '../../auth/authContext'
import { useLoadMoreOnScroll, usePagedList } from '../../hooks/usePagedList'

/** 一覧の1件。usePagedList が id で重複を省くため、会話の ID を id として持たせる */
export type ConversationRow = ConversationListItem & { id: number }

/**
 * DM の一覧（DM一覧・メッセージリクエスト・送信したリクエスト・ピン留めした相談）の読み込み。無限スクロールで続きを読む。
 * pinnedOnly なら、自分がピン留めした相談だけ（docs/dm.md「ピン留め」）
 */
export function useConversationList(filter: ConversationFilter, query: string, pinnedOnly = false) {
  const { token } = useAuth()
  const list = usePagedList<ConversationRow>(
    (page, authToken, signal) =>
      fetchConversations(filter, query, page, authToken, signal, pinnedOnly ? { pinnedOnly } : {}).then((result) => ({
        items: result.conversations.map((c) => ({ ...c, id: c.conversationId })),
        hasNext: result.hasNext,
      })),
    token,
  )
  const sentinelRef = useLoadMoreOnScroll(list.loadMore)
  return { ...list, sentinelRef }
}
