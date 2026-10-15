import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ConversationListItem, ConversationListResponse, ConversationSummary } from '../../api/conversations'
import { DmSummaryContext } from '../../components/dmSummaryContext'
import DmPinnedPage from './DmPinnedPage'

/*
 * ピン留めした相談の一覧（/dm/pinned。docs/dm.md「ピン留め」）の確認。
 * DM一覧の見出しの左上のマークから開く。ピン留めした相談だけを、DM一覧と同じ並びで出す
 */

const fetchConversations = vi.fn<(...args: unknown[]) => Promise<ConversationListResponse>>()
vi.mock('../../api/conversations', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/conversations')>()),
  fetchConversations: (...args: unknown[]) => fetchConversations(...args),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token' }) }))

const SUMMARY: ConversationSummary = { unreadMessageCount: 0, receivedRequestCount: 0, sentRequestCount: 0, pinnedCount: 2 }

function item(id: number, username: string, overrides: Partial<ConversationListItem> = {}): ConversationListItem {
  return {
    conversationId: id,
    status: 'ACTIVE',
    requestedByMe: true,
    partner: { id: id + 100, username, displayName: username, profileImageUrl: null },
    lastMessageBody: `${username} とのメッセージ`,
    lastMessageHasImage: false,
    lastMessageAt: '2026-10-15T10:00:00',
    unreadCount: 0,
    requestedAt: '2026-10-01T10:00:00',
    latestUnread: null,
    pinned: true,
    muted: false,
    ...overrides,
  }
}

beforeEach(() => {
  vi.stubGlobal(
    'IntersectionObserver',
    // 一覧の続きは画面の下端が見えたら読む。テストでは見えたことにして、最初のページを読ませる（BlockListPage のテストと同じ）
    class {
      private readonly callback: IntersectionObserverCallback
      constructor(callback: IntersectionObserverCallback) {
        this.callback = callback
      }
      observe() {
        this.callback([{ isIntersecting: true } as IntersectionObserverEntry], this as unknown as IntersectionObserver)
      }
      disconnect() {}
    },
  )
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  fetchConversations.mockReset()
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/dm/pinned']}>
      <DmSummaryContext.Provider value={{ summary: SUMMARY, refresh: () => {} }}>
        <DmPinnedPage />
      </DmSummaryContext.Provider>
    </MemoryRouter>,
  )
}

describe('ピン留めした相談の一覧', () => {
  it('ピン留めした相談だけを読み、件数と、終了した相談も含めて出す', async () => {
    fetchConversations.mockResolvedValue({
      conversations: [item(1, 'haru_st'), item(2, 'yui_m', { status: 'ENDED' })],
      page: 0,
      size: 20,
      hasNext: false,
    })
    const { container } = renderPage()

    const first = await screen.findByText('@haru_st')
    expect(screen.getByRole('heading', { name: /ピン留めした相談/ }).textContent).toContain('2件')
    expect(fetchConversations.mock.calls[0][0]).toBe('chats')
    expect(fetchConversations.mock.calls[0][5]).toMatchObject({ pinnedOnly: true })
    const rows = container.querySelectorAll('.dm-list li')
    expect(rows).toHaveLength(2)
    expect(within(rows[1] as HTMLElement).getByText('終了')).toBeTruthy()
    // 行にはピン留めの印を出す
    expect(within(first.closest('a') as HTMLElement).getByLabelText('ピン留め中')).toBeTruthy()
    expect((first.closest('a') as HTMLAnchorElement).getAttribute('href')).toBe('/dm/1')
  })

  it('通知をオフにした相談には、通知オフの印を出す', async () => {
    fetchConversations.mockResolvedValue({
      conversations: [item(1, 'haru_st', { muted: true }), item(2, 'yui_m')],
      page: 0,
      size: 20,
      hasNext: false,
    })
    renderPage()

    const muted = (await screen.findByText('@haru_st')).closest('a') as HTMLElement
    const normal = screen.getByText('@yui_m').closest('a') as HTMLElement
    expect(within(muted).getByLabelText('通知オフ')).toBeTruthy()
    expect(within(normal).queryByLabelText('通知オフ')).toBeNull()
  })

  it('ピン留めがなければ、その旨を出す', async () => {
    fetchConversations.mockResolvedValue({ conversations: [], page: 0, size: 20, hasNext: false })
    renderPage()

    expect(await screen.findByText('ピン留めした相談はありません')).toBeTruthy()
  })
})
