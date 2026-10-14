import { cleanup, render, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { NotificationSummary } from '../../api/notifications'
import { DmSummaryContext } from '../../components/dmSummaryContext'
import { NotificationSummaryContext } from '../../components/notificationSummaryContext'
import HomePage from './HomePage'

// ベルの表示だけを確かめるため、ログインしていない状態にして API を呼ばせない
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: null }) }))

beforeEach(() => {
  // jsdom には無限スクロールで使う IntersectionObserver がないため、何もしないものに置き換える
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      observe() {}
      disconnect() {}
    },
  )
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function renderHome(summary: NotificationSummary) {
  return render(
    <MemoryRouter>
      <DmSummaryContext.Provider value={{ summary: null, refresh: () => {} }}>
        <NotificationSummaryContext.Provider value={{ summary, refresh: () => {} }}>
          <HomePage />
        </NotificationSummaryContext.Provider>
      </DmSummaryContext.Provider>
    </MemoryRouter>,
  )
}

function bell(container: HTMLElement): HTMLElement {
  const header = container.querySelector('header.home-header') as HTMLElement
  return within(header).getByRole('link', { name: /^通知/ })
}

describe('ホーム画面の通知ベル（docs/notification.md）', () => {
  it('通知の未読とお知らせの未読の合計をバッジに出す', () => {
    const { container } = renderHome({ unreadCount: 3, unreadAnnouncementCount: 2 })

    expect(bell(container).getAttribute('aria-label')).toBe('通知（未読 5件）')
    expect(bell(container).querySelector('.home-bell-badge')?.textContent).toBe('5')
  })

  it('お知らせの未読だけでもバッジを出す', () => {
    const { container } = renderHome({ unreadCount: 0, unreadAnnouncementCount: 1 })

    expect(bell(container).querySelector('.home-bell-badge')?.textContent).toBe('1')
  })

  it('どちらも0件ならバッジを出さない', () => {
    const { container } = renderHome({ unreadCount: 0, unreadAnnouncementCount: 0 })

    expect(bell(container).getAttribute('aria-label')).toBe('通知')
    expect(bell(container).querySelector('.home-bell-badge')).toBeNull()
  })

  it('合計が99件を超えたら 99+ と出す', () => {
    const { container } = renderHome({ unreadCount: 98, unreadAnnouncementCount: 2 })

    expect(bell(container).querySelector('.home-bell-badge')?.textContent).toBe('99+')
  })
})
