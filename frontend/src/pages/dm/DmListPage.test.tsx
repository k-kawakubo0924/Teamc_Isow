import { cleanup, render, within } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DmSummaryContext } from '../../components/dmSummaryContext'
import { NotificationSummaryContext } from '../../components/notificationSummaryContext'
import HomePage from '../home/HomePage'
import DmListPage from './DmListPage'

// 見出しの表示だけを確かめるため、ログインしていない状態にして API を呼ばせない
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

/** 下部ナビのある画面と同じく、DM の件数と通知の未読件数を渡して描画する（通知の未読は3件） */
function renderPage(page: ReactElement) {
  return render(
    <MemoryRouter>
      <DmSummaryContext.Provider value={{ summary: null, refresh: () => {} }}>
        <NotificationSummaryContext.Provider value={{ summary: { unreadCount: 3 }, refresh: () => {} }}>
          {page}
        </NotificationSummaryContext.Provider>
      </DmSummaryContext.Provider>
    </MemoryRouter>,
  )
}

describe('DM一覧の見出し', () => {
  it('左上のマーク（ピン留めの一覧を開くもの。未実装）を置かず、見出しはロゴだけ', () => {
    const { container } = renderPage(<DmListPage />)

    const header = container.querySelector('header.dm-header') as HTMLElement
    expect(header).not.toBeNull()
    // 押せるものがない
    expect(within(header).queryAllByRole('link')).toHaveLength(0)
    expect(within(header).queryAllByRole('button')).toHaveLength(0)
    // ベルの絵や「通知」の名前も残っていない
    expect(header.querySelector('svg')).toBeNull()
    expect(within(header).queryByLabelText(/通知/)).toBeNull()
    // 中身はロゴだけ（中央寄せは .dm-header の flex で行う）
    expect(header.children).toHaveLength(1)
    expect(within(header).getByRole('heading', { name: 'ISHO' })).toBeTruthy()
  })
})

describe('ホーム画面の通知ベル（DM一覧の変更に巻き込まれていないこと）', () => {
  it('左上のベルは今までどおり /notifications へのリンクで、未読件数のバッジが出る', () => {
    const { container } = renderPage(<HomePage />)

    const header = container.querySelector('header.home-header') as HTMLElement
    const bell = within(header).getByRole('link', { name: '通知（未読 3件）' })
    expect(bell.getAttribute('href')).toBe('/notifications')
    expect(bell.querySelector('.home-bell-badge')?.textContent).toBe('3')
  })
})
