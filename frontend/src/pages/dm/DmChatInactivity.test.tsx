import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ConversationDetail } from '../../api/conversations'
import DmChatPage from './DmChatPage'

/*
 * 30日やり取りがなかったため自動で終了した相談（docs/dm.md「30日やり取りがない相談の自動終了」）の表示。
 * サーバーは「自動終了かどうか」だけを返す（ブロック・利用停止などの理由は返さない）。
 * 自動終了でなければ、今までどおり「この会話は終了しました」とだけ出す
 */

let detail: ConversationDetail
vi.mock('./useChat', () => ({
  useChat: () => ({
    detail,
    setDetail: vi.fn(),
    messages: [],
    append: vi.fn(),
    hasOlder: false,
    loadingOlder: false,
    loadOlder: vi.fn(),
    loadError: null,
    poll: vi.fn(async () => {}),
  }),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token' }) }))

function ended(endedByInactivity: boolean): ConversationDetail {
  return {
    conversationId: 10,
    status: 'ENDED',
    requestedByMe: true,
    partner: { id: 2, username: 'haru_st', displayName: 'はる', profileImageUrl: null },
    requestedAt: '2026-09-01T10:00:00',
    respondedAt: '2026-09-01T11:00:00',
    endedAt: '2026-10-11T10:15:00',
    endedByInactivity,
  }
}

beforeEach(() => {
  Element.prototype.scrollTo = () => {}
})

afterEach(() => {
  cleanup()
})

function renderChat(conversation: ConversationDetail) {
  detail = conversation
  return render(
    <MemoryRouter initialEntries={['/dm/10']}>
      <Routes>
        <Route path="/dm/:conversationId" element={<DmChatPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('自動終了した相談', () => {
  it('30日間やり取りがなかったため自動で終了したことを、入力欄の代わりに出す', () => {
    renderChat(ended(true))

    expect(screen.getByText('30日間やり取りがなかったため、自動で終了しました（閲覧のみ）')).toBeTruthy()
  })

  it('自動終了でなければ、今までどおり終了したことだけを出す（理由は出さない）', () => {
    const { container } = renderChat(ended(false))

    expect(screen.getByText('この会話は終了しました（閲覧のみ）')).toBeTruthy()
    expect(container.textContent).not.toContain('30日')
  })
})
