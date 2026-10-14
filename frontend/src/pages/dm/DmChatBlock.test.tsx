import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ConversationDetail } from '../../api/conversations'
import DmChatPage from './DmChatPage'

/*
 * チャット画面のメニューからのブロック（docs/dm.md「ブロック」）の確認。
 * 会話の読み込み・取り直し（useChat）は差し替え、メニューの動きだけを確かめる
 */

const poll = vi.fn<() => Promise<void>>()
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
    poll,
  }),
}))
const blockUser = vi.fn<(id: number) => Promise<void>>()
vi.mock('../../api/blocks', () => ({ blockUser: (id: number) => blockUser(id) }))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token' }) }))

function conversation(status: ConversationDetail['status']): ConversationDetail {
  return {
    conversationId: 10,
    status,
    requestedByMe: true,
    partner: { id: 2, username: 'haru_st', displayName: 'はる', profileImageUrl: null },
    requestedAt: '2026-10-10T10:00:00',
    respondedAt: '2026-10-10T11:00:00',
    endedAt: status === 'ENDED' ? '2026-10-11T10:00:00' : null,
  }
}

beforeEach(() => {
  poll.mockResolvedValue(undefined)
})

afterEach(() => {
  cleanup()
  poll.mockReset()
  blockUser.mockReset()
})

function renderChat(status: ConversationDetail['status']) {
  detail = conversation(status)
  return render(
    <MemoryRouter initialEntries={['/dm/10']}>
      <Routes>
        <Route path="/dm/:conversationId" element={<DmChatPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('チャット画面のメニュー', () => {
  it('進行中の会話では「会話を終了する」と「ブロック」がある', () => {
    renderChat('ACTIVE')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))

    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual(['会話を終了する', 'ブロック'])
  })

  it('終了した会話でも「ブロック」はできる（「会話を終了する」は出さない）', () => {
    renderChat('ENDED')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))

    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual(['ブロック'])
  })

  it('「ブロック」で確かめてからブロックし、会話の状態を取り直す', async () => {
    blockUser.mockResolvedValue(undefined)
    renderChat('ACTIVE')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'ブロック' }))
    const dialog = screen.getByRole('alertdialog')
    expect(dialog.textContent).toContain('@haru_st さんをブロックしますか？')
    expect(dialog.textContent).toContain('進行中の相談は終了します')
    expect(dialog.textContent).toContain('相手には通知されません')
    expect(blockUser).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: 'ブロックする' }))

    await vi.waitFor(() => expect(poll).toHaveBeenCalled())
    expect(blockUser).toHaveBeenCalledWith(2)
    expect(screen.queryByRole('alertdialog')).toBeNull()
  })
})
