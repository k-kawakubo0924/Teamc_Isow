import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { NotificationItem } from '../../api/notifications'
import { NotificationSummaryContext } from '../../components/notificationSummaryContext'
import NotificationPage from './NotificationPage'

/*
 * 運営からの通知（投稿の非公開化・公開に戻す。docs/admin.md「6. 措置」）の確認。
 * 「誰が」（actor）がないため、ユーザー名ではなく「運営」と出し、投稿の詳細へ移る
 */

vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true }) }))

let notifications: NotificationItem[]

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

async function fakeFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const url = new URL(String(input))
  const method = init?.method ?? 'GET'
  if (method === 'GET' && url.pathname === '/api/notifications') {
    return json(200, { notifications, page: 0, size: 20, hasNext: false })
  }
  if (method === 'POST') return new Response(null, { status: 204 })
  return json(404, null)
}

beforeEach(() => {
  notifications = [
    {
      id: 2,
      type: 'POST_UNHIDDEN',
      actor: null,
      post: { id: 5, thumbnailUrl: null },
      conversationId: null,
      notifiedAt: '2026-10-12T10:00:00',
      read: false,
    },
    {
      id: 1,
      type: 'POST_HIDDEN',
      actor: null,
      post: { id: 5, thumbnailUrl: null },
      conversationId: null,
      notifiedAt: '2026-10-11T10:00:00',
      read: true,
    },
  ]
  vi.stubGlobal('fetch', vi.fn(fakeFetch))
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

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/notifications']}>
      <NotificationSummaryContext.Provider
        value={{ summary: { unreadCount: 1, unreadAnnouncementCount: 0 }, refresh: () => {} }}
      >
        <Routes>
          <Route path="/notifications" element={<NotificationPage />} />
          <Route path="*" element={<Where />} />
        </Routes>
      </NotificationSummaryContext.Provider>
    </MemoryRouter>,
  )
}

describe('運営からの通知', () => {
  it('ユーザー名の代わりに「運営」と出し、非公開にした・公開に戻したことを伝える', async () => {
    const { container } = renderPage()

    await screen.findByText(/あなたの投稿を非公開にしました/)
    const texts = [...container.querySelectorAll('.notification-text')].map((e) => e.textContent)
    expect(texts).toEqual(['運営 があなたの投稿の非公開を解除しました', '運営 があなたの投稿を非公開にしました'])
    expect(container.textContent).not.toContain('@')
    expect(container.textContent).not.toMatch(/null|undefined/)
  })

  it('押すと、その投稿の詳細へ移る', async () => {
    renderPage()

    fireEvent.click(await screen.findByText(/あなたの投稿を非公開にしました/))

    expect((await screen.findByTestId('where')).textContent).toBe('/posts/5')
  })
})
