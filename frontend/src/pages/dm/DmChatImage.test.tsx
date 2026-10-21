import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ConversationDetail, Message } from '../../api/conversations'
import { clearAuthorizedImages } from '../../hooks/authorizedImageCache'
import DmChatPage from './DmChatPage'

/*
 * チャット画面の画像（docs/dm.md「画像」）。DM の画像は会話の当事者2人にだけ配るため、/uploads の URL ではなく、
 * 認証付きの API から中身を取得し、その場限りの URL にして表示する。会話の読み込み・取り直し（useChat）は差し替える
 */

let detail: ConversationDetail
let messages: Message[] = []
vi.mock('./useChat', () => ({
  useChat: () => ({
    detail,
    setDetail: vi.fn(),
    messages,
    append: vi.fn(),
    hasOlder: false,
    loadingOlder: false,
    loadOlder: vi.fn(),
    loadError: null,
    poll: vi.fn(),
  }),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token' }) }))
const fetchMessageImage = vi.fn<(conversationId: number, messageId: number, token: string) => Promise<Blob>>()
vi.mock('../../api/conversations', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/conversations')>()),
  fetchMessageImage: (conversationId: number, messageId: number, token: string) =>
    fetchMessageImage(conversationId, messageId, token),
}))

function message(id: number, hasImage: boolean, body: string | null = null): Message {
  return { id, senderId: 2, mine: false, body, hasImage, sentAt: '2026-10-10T12:00:00', readAt: null }
}

beforeEach(() => {
  detail = {
    conversationId: 10,
    status: 'ACTIVE',
    requestedByMe: true,
    partner: { id: 2, username: 'haru_st', displayName: 'はる', profileImageUrl: null },
    requestedAt: '2026-10-10T10:00:00',
    respondedAt: '2026-10-10T11:00:00',
    endedAt: null,
    endedByInactivity: false,
    pinned: false,
    muted: false,
  }
  messages = []
  Element.prototype.scrollTo = () => {}
  let created = 0
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => `blob:dm-${++created}`), revokeObjectURL: vi.fn() }))
})

afterEach(() => {
  cleanup()
  clearAuthorizedImages()
  fetchMessageImage.mockReset()
  vi.unstubAllGlobals()
})

function renderChat() {
  return render(
    <MemoryRouter initialEntries={['/dm/10']}>
      <Routes>
        <Route path="/dm/:conversationId" element={<DmChatPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('チャット画面の画像', () => {
  it('画像は認証付きの API から取得し、その場限りの URL で表示する（/uploads の URL は使わない）', async () => {
    fetchMessageImage.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    messages = [message(5, true)]

    renderChat()

    const image = (await screen.findByAltText('送信された画像')) as HTMLImageElement
    expect(image.getAttribute('src')).toBe('blob:dm-1')
    expect(fetchMessageImage).toHaveBeenCalledWith(10, 5, 'test-token')
  })

  it('読み込み中は、画像と同じ場所に枠を出す（読み込み後に画面が大きく動かないように）', () => {
    fetchMessageImage.mockReturnValue(new Promise(() => {}))
    messages = [message(5, true)]

    const { container } = renderChat()

    expect(container.querySelector('.chat-image-loading')).not.toBeNull()
    expect(screen.queryByAltText('送信された画像')).toBeNull()
  })

  it('読み込めなければ「画像を読み込めませんでした」と出す', async () => {
    fetchMessageImage.mockRejectedValue(new Error('404'))
    messages = [message(5, true)]

    renderChat()

    expect(await screen.findByText('画像を読み込めませんでした')).toBeTruthy()
  })

  it('画像のないメッセージでは取りに行かない', () => {
    messages = [message(5, false, '文字だけ')]

    renderChat()

    expect(screen.getByText('文字だけ')).toBeTruthy()
    expect(fetchMessageImage).not.toHaveBeenCalled()
  })

  it('取り直し（5秒ごと）で一覧が作り直されても、同じ画像を取りに行き直さない', async () => {
    fetchMessageImage.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    messages = [message(5, true)]
    const { rerender } = renderChat()
    await screen.findByAltText('送信された画像')

    // 取り直しで、同じメッセージが新しいオブジェクトとして届き、新しいメッセージが足される
    messages = [message(5, true), message(6, false, '新しいメッセージ')]
    rerender(
      <MemoryRouter initialEntries={['/dm/10']}>
        <Routes>
          <Route path="/dm/:conversationId" element={<DmChatPage />} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByText('新しいメッセージ')

    await waitFor(() => expect(screen.getByAltText('送信された画像').getAttribute('src')).toBe('blob:dm-1'))
    expect(fetchMessageImage).toHaveBeenCalledTimes(1)
  })

  it('画面を閉じて開き直しても、画面を開いている間（再読み込みするまで）は取りに行き直さない', async () => {
    fetchMessageImage.mockResolvedValue(new Blob(['x'], { type: 'image/png' }))
    messages = [message(5, true)]
    renderChat()
    await screen.findByAltText('送信された画像')
    cleanup()

    renderChat()

    expect((await screen.findByAltText('送信された画像')).getAttribute('src')).toBe('blob:dm-1')
    expect(fetchMessageImage).toHaveBeenCalledTimes(1)
  })
})
