import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { ConversationDetail, ConversationSetting, Message } from '../../api/conversations'
import DmChatPage from './DmChatPage'

/*
 * チャット画面のメニューからのブロック（docs/dm.md「ブロック」）の確認。
 * 会話の読み込み・取り直し（useChat）は差し替え、メニューの動きだけを確かめる
 */

const poll = vi.fn<() => Promise<void>>()
let detail: ConversationDetail
let messages: Message[] = []
const setDetail = vi.fn()
vi.mock('./useChat', () => ({
  useChat: () => ({
    detail,
    setDetail,
    messages,
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
const pinConversation = vi.fn<(id: number) => Promise<ConversationSetting>>()
const unpinConversation = vi.fn<(id: number) => Promise<ConversationSetting>>()
const muteConversation = vi.fn<(id: number) => Promise<ConversationSetting>>()
const unmuteConversation = vi.fn<(id: number) => Promise<ConversationSetting>>()
vi.mock('../../api/conversations', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/conversations')>()),
  pinConversation: (id: number) => pinConversation(id),
  unpinConversation: (id: number) => unpinConversation(id),
  muteConversation: (id: number) => muteConversation(id),
  unmuteConversation: (id: number) => unmuteConversation(id),
}))
vi.mock('../../api/reports', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/reports')>()),
  reportTarget: vi.fn(),
}))

function conversation(status: ConversationDetail['status']): ConversationDetail {
  return {
    conversationId: 10,
    status,
    requestedByMe: true,
    partner: { id: 2, username: 'haru_st', displayName: 'はる', profileImageUrl: null },
    requestedAt: '2026-10-10T10:00:00',
    respondedAt: '2026-10-10T11:00:00',
    endedAt: status === 'ENDED' ? '2026-10-11T10:00:00' : null,
    endedByInactivity: false,
    pinned: false,
    muted: false,
  }
}

beforeEach(() => {
  poll.mockResolvedValue(undefined)
  messages = []
  // jsdom にはスクロールの位置がないため、一番下までのスクロールは何もしない
  Element.prototype.scrollTo = () => {}
})

afterEach(() => {
  cleanup()
  poll.mockReset()
  blockUser.mockReset()
  setDetail.mockReset()
  pinConversation.mockReset()
  unpinConversation.mockReset()
  muteConversation.mockReset()
  unmuteConversation.mockReset()
})

function renderChat(status: ConversationDetail['status']) {
  return renderChatWith(conversation(status))
}

function renderChatWith(conversationDetail: ConversationDetail) {
  detail = conversationDetail
  return render(
    <MemoryRouter initialEntries={['/dm/10']}>
      <Routes>
        <Route path="/dm/:conversationId" element={<DmChatPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('チャット画面のメニュー', () => {
  it('進行中の会話では「ピン留めする」「通知をオフにする」「会話を終了する」「ブロック」がある', () => {
    renderChat('ACTIVE')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))

    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual([
      'ピン留めする',
      '通知をオフにする',
      '会話を終了する',
      'ブロック',
    ])
  })

  it('「通知をオフにする」で確認なしにオフにし、会話の設定を更新する（相手には分からない）', async () => {
    muteConversation.mockResolvedValue({ conversationId: 10, pinned: false, muted: true })
    renderChat('ACTIVE')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    fireEvent.click(screen.getByRole('menuitem', { name: '通知をオフにする' }))

    await vi.waitFor(() => expect(setDetail).toHaveBeenCalled())
    expect(muteConversation).toHaveBeenCalledWith(10)
    const update = setDetail.mock.calls[0][0] as (prev: ConversationDetail) => ConversationDetail
    expect(update(conversation('ACTIVE'))).toMatchObject({ muted: true })
  })

  it('通知オフなら「通知をオンにする」を出す。終了した後でもオンに戻せる', async () => {
    unmuteConversation.mockResolvedValue({ conversationId: 10, pinned: false, muted: false })
    renderChatWith({ ...conversation('ENDED'), muted: true })

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual([
      'ピン留めする',
      '通知をオンにする',
      'ブロック',
    ])
    fireEvent.click(screen.getByRole('menuitem', { name: '通知をオンにする' }))

    await vi.waitFor(() => expect(unmuteConversation).toHaveBeenCalledWith(10))
  })

  it('終了した会話でも「ピン留めする」と「ブロック」はできる（「会話を終了する」は出さない）', () => {
    renderChat('ENDED')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))

    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual(['ピン留めする', 'ブロック'])
  })

  it('申請中の会話ではピン留めを出さない', () => {
    renderChat('REQUESTED')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))

    expect(screen.queryByRole('menuitem', { name: /ピン留め/ })).toBeNull()
  })

  it('「ピン留めする」で確認なしにピン留めし、会話の設定を更新する', async () => {
    pinConversation.mockResolvedValue({ conversationId: 10, pinned: true, muted: false })
    renderChat('ACTIVE')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'ピン留めする' }))

    await vi.waitFor(() => expect(setDetail).toHaveBeenCalled())
    expect(pinConversation).toHaveBeenCalledWith(10)
    const update = setDetail.mock.calls[0][0] as (prev: ConversationDetail) => ConversationDetail
    expect(update(conversation('ACTIVE'))).toMatchObject({ pinned: true, muted: false })
  })

  it('ピン留め中なら「ピン留めを外す」を出す', async () => {
    unpinConversation.mockResolvedValue({ conversationId: 10, pinned: false, muted: false })
    detail = { ...conversation('ACTIVE'), pinned: true }
    renderChatWith(detail)

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'ピン留めを外す' }))

    await vi.waitFor(() => expect(unpinConversation).toHaveBeenCalledWith(10))
  })

  it('上限（10件）で断られたら、サーバーの文言を出す', async () => {
    pinConversation.mockRejectedValue(
      new ApiError(409, { message: 'ピン留めできるのは10件までです。', errors: {}, reason: 'PIN_LIMIT_REACHED' }, 'ピン留めできるのは10件までです。'),
    )
    renderChat('ACTIVE')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'ピン留めする' }))

    expect((await screen.findByRole('alert')).textContent).toBe('ピン留めできるのは10件までです。')
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

describe('メッセージの通報', () => {
  function message(id: number, mine: boolean, body: string): Message {
    return { id, senderId: mine ? 1 : 2, mine, body, imageUrl: null, sentAt: '2026-10-10T12:00:00', readAt: null }
  }

  it('相手のメッセージを押すと「通報」が出て、そのメッセージを対象にした通報の入力を出す', () => {
    messages = [message(30, false, '相手のメッセージ'), message(31, true, '自分のメッセージ')]
    renderChat('ACTIVE')

    fireEvent.click(screen.getByText('相手のメッセージ'))
    fireEvent.click(screen.getByRole('button', { name: 'このメッセージを通報' }))

    const dialog = screen.getByRole('dialog', { name: 'メッセージを通報' })
    expect(within(dialog).getByLabelText('嫌がらせ・誹謗中傷')).toBeTruthy()
  })

  it('自分のメッセージは押しても「通報」を出さない', () => {
    messages = [message(31, true, '自分のメッセージ')]
    renderChat('ACTIVE')

    fireEvent.click(screen.getByText('自分のメッセージ'))

    expect(screen.queryByRole('button', { name: 'このメッセージを通報' })).toBeNull()
  })
})
