import { fetchConversations, type ConversationFilter, type ConversationListItem } from '../../api/conversations'
import { useAuth } from '../../auth/authContext'
import { useLoadMoreOnScroll, usePagedList } from '../../hooks/usePagedList'

/** 一覧の1件。usePagedList が id で重複を省くため、会話の ID を id として持たせる */
export type ConversationRow = ConversationListItem & { id: number }

/** DM の一覧（DM一覧・メッセージリクエスト・送信したリクエスト）の読み込み。無限スクロールで続きを読む */
export function useConversationList(filter: ConversationFilter, query: string) {
  const { token } = useAuth()
  const list = usePagedList<ConversationRow>(
    (page, authToken, signal) =>
      fetchConversations(filter, query, page, authToken, signal).then((result) => ({
        items: result.conversations.map((c) => ({ ...c, id: c.conversationId })),
        hasNext: result.hasNext,
      })),
    token,
  )
  const sentinelRef = useLoadMoreOnScroll(list.loadMore)
  return { ...list, sentinelRef }
}
