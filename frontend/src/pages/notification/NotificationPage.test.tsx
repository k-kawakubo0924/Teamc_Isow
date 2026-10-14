import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import type { NotificationSummary } from '../../api/notifications'
import { NotificationSummaryContext } from '../../components/notificationSummaryContext'
import NotificationPage from './NotificationPage'

/*
 * 通知一覧の「お知らせ」タブ（docs/notification.md「お知らせ」）の確認。
 * fetch を、お知らせ・通知の API と同じ形で答える偽のサーバーに置き換える
 */

vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true }) }))

type Announcement = { id: number; title: string; body: string; publishedAt: string; read: boolean }

/** サーバーにあるお知らせ（新しい順） */
let stored: Announcement[]
let requests: { method: string; path: string }[]
/** 次の既読の通信を失敗させる */
let failNextRead: boolean
let refreshSummary: Mock<() => void>

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

async function fakeFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const url = new URL(String(input))
  const method = init?.method ?? 'GET'
  requests.push({ method, path: url.pathname })
  if (method === 'GET' && url.pathname === '/api/announcements') {
    return json(200, { announcements: stored, page: 0, size: 20, hasNext: false })
  }
  if (method === 'GET' && url.pathname === '/api/notifications') {
    return json(200, { notifications: [], page: 0, size: 20, hasNext: false })
  }
  const read = url.pathname.match(/^\/api\/announcements\/(\d+)\/read$/)
  if (method === 'POST' && read) {
    if (failNextRead) {
      failNextRead = false
      return json(500, null)
    }
    return new Response(null, { status: 204 })
  }
  return json(404, null)
}

beforeEach(() => {
  stored = [
    {
      id: 3,
      title: 'メンテナンスのお知らせ',
      body: '10月10日 2:00〜4:00 にメンテナンスを行います。\nご不便をおかけします。',
      publishedAt: '2026-10-08T09:05:00',
      read: false,
    },
    {
      id: 1,
      title: 'サービス開始',
      body: '<b>ISHO</b> を始めました',
      publishedAt: '2026-10-01T18:00:00',
      read: true,
    },
  ]
  requests = []
  failNextRead = false
  refreshSummary = vi.fn<() => void>()
  vi.stubGlobal('fetch', vi.fn(fakeFetch))
  // 無限スクロールの目印は、置いたらすぐ画面に入ったものとして扱う（最初のページを読み込ませるため）
  vi.stubGlobal(
    'IntersectionObserver',
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
})

function renderPage(summary: NotificationSummary = { unreadCount: 2, unreadAnnouncementCount: 1 }) {
  return render(
    <MemoryRouter>
      <NotificationSummaryContext.Provider value={{ summary, refresh: refreshSummary }}>
        <NotificationPage />
      </NotificationSummaryContext.Provider>
    </MemoryRouter>,
  )
}

function openNewsTab() {
  fireEvent.click(screen.getByRole('tab', { name: /お知らせ/ }))
}

function announcementButton(title: string): HTMLElement {
  return screen.getByRole('button', { name: new RegExp(title) })
}

function readRequests() {
  return requests.filter((r) => r.method === 'POST')
}

describe('タブのバッジ', () => {
  it('「いいね・フォロー」には通知の未読件数、「お知らせ」にはお知らせの未読件数を、それぞれ出す', () => {
    renderPage({ unreadCount: 2, unreadAnnouncementCount: 5 })

    const activity = screen.getByRole('tab', { name: /いいね・フォロー/ })
    const news = screen.getByRole('tab', { name: /お知らせ/ })
    expect(within(activity).getByLabelText('未読 2件').textContent).toBe('2')
    expect(within(news).getByLabelText('未読 5件').textContent).toBe('5')
  })

  it('お知らせの未読がなければ、「お知らせ」にはバッジを出さない', () => {
    renderPage({ unreadCount: 2, unreadAnnouncementCount: 0 })

    expect(within(screen.getByRole('tab', { name: /お知らせ/ })).queryByLabelText(/未読/)).toBeNull()
  })
})

describe('「お知らせ」タブの一覧', () => {
  it('新しい順に、題名と発行日だけを出し、本文は閉じている', async () => {
    const { container } = renderPage()
    openNewsTab()

    await screen.findByText('メンテナンスのお知らせ')
    const items = container.querySelectorAll('.notification-announcement')
    expect(items).toHaveLength(2)
    expect(items[0].textContent).toContain('メンテナンスのお知らせ')
    expect(items[0].textContent).toContain('2026/10/08')
    expect(items[1].textContent).toContain('サービス開始')
    expect(items[1].textContent).toContain('2026/10/01')
    expect(screen.queryByText(/メンテナンスを行います/)).toBeNull()
    expect(announcementButton('メンテナンスのお知らせ').getAttribute('aria-expanded')).toBe('false')
    expect(requests.some((r) => r.path === '/api/announcements')).toBe(true)
  })

  it('未読は白、既読は暗めの背景にする（通知と同じ）', async () => {
    const { container } = renderPage()
    openNewsTab()

    await screen.findByText('メンテナンスのお知らせ')
    const items = container.querySelectorAll('.notification-announcement')
    expect(items[0].classList.contains('notification-announcement-read')).toBe(false)
    expect(items[1].classList.contains('notification-announcement-read')).toBe(true)
  })

  it('お知らせには「未読に戻す」「削除」のメニューを出さない', async () => {
    renderPage()
    openNewsTab()

    await screen.findByText('メンテナンスのお知らせ')
    expect(screen.queryByRole('button', { name: 'メニュー' })).toBeNull()
  })

  it('お知らせがなければ「お知らせはありません」を出す', async () => {
    stored = []
    renderPage()
    openNewsTab()

    expect(await screen.findByText('お知らせはありません')).toBeTruthy()
  })
})

describe('お知らせを開く', () => {
  it('押すとその場で本文が開き、改行はそのまま出し、HTML としては扱わない', async () => {
    const { container } = renderPage()
    openNewsTab()
    await screen.findByText('メンテナンスのお知らせ')

    fireEvent.click(announcementButton('メンテナンスのお知らせ'))
    fireEvent.click(announcementButton('サービス開始'))

    expect(announcementButton('メンテナンスのお知らせ').getAttribute('aria-expanded')).toBe('true')
    const bodies = container.querySelectorAll('.notification-announcement-body')
    expect(bodies[0].textContent).toBe('10月10日 2:00〜4:00 にメンテナンスを行います。\nご不便をおかけします。')
    expect(bodies[1].textContent).toBe('<b>ISHO</b> を始めました')
    expect(container.querySelector('.notification-announcement-body b')).toBeNull()
  })

  it('もう一度押すと閉じる', async () => {
    renderPage()
    openNewsTab()
    await screen.findByText('メンテナンスのお知らせ')

    fireEvent.click(announcementButton('メンテナンスのお知らせ'))
    fireEvent.click(announcementButton('メンテナンスのお知らせ'))

    expect(announcementButton('メンテナンスのお知らせ').getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByText(/メンテナンスを行います/)).toBeNull()
  })

  it('未読のお知らせを開くと既読にして、未読件数を取り直す', async () => {
    const { container } = renderPage()
    openNewsTab()
    await screen.findByText('メンテナンスのお知らせ')

    fireEvent.click(announcementButton('メンテナンスのお知らせ'))

    expect(container.querySelectorAll('.notification-announcement')[0].classList)
      .toContain('notification-announcement-read')
    await waitFor(() => expect(refreshSummary).toHaveBeenCalled())
    expect(readRequests()).toEqual([{ method: 'POST', path: '/api/announcements/3/read' }])
  })

  it('既読のお知らせを開いても、既読の通信はしない', async () => {
    renderPage()
    openNewsTab()
    await screen.findByText('サービス開始')

    fireEvent.click(announcementButton('サービス開始'))

    expect(screen.getByText('<b>ISHO</b> を始めました')).toBeTruthy()
    expect(readRequests()).toHaveLength(0)
  })

  it('既読にできなくても本文は開いたままにする', async () => {
    failNextRead = true
    renderPage()
    openNewsTab()
    await screen.findByText('メンテナンスのお知らせ')

    fireEvent.click(announcementButton('メンテナンスのお知らせ'))

    await waitFor(() => expect(readRequests()).toHaveLength(1))
    expect(screen.getByText(/メンテナンスを行います/)).toBeTruthy()
  })
})
