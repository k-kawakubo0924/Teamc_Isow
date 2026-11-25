import { cleanup, render, within } from '@testing-library/react'
import type { ReactElement } from 'react'
import type { ConversationSummary } from '../../api/conversations'
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

/** DM の件数（ピン留めは3件。design/DMlist.png の見出しのマークのバッジ「3」） */
const SUMMARY: ConversationSummary = { unreadMessageCount: 2, receivedRequestCount: 2, sentRequestCount: 2, pinnedCount: 3 }

/** 下部ナビのある画面と同じく、DM の件数と通知の未読件数を渡して描画する（通知の未読は3件） */
function renderPage(page: ReactElement, summary: ConversationSummary | null = SUMMARY) {
  return render(
    <MemoryRouter>
      <DmSummaryContext.Provider value={{ summary, refresh: () => {} }}>
        <NotificationSummaryContext.Provider value={{ summary: { unreadCount: 3, unreadAnnouncementCount: 0 }, refresh: () => {} }}>
          {page}
        </NotificationSummaryContext.Provider>
      </DmSummaryContext.Provider>
    </MemoryRouter>,
  )
}

/*
 * 経緯：ピン留めを作る前は、この見出しの左上のマーク（ピン留めした相談の一覧を開くもの）をいったん削除し、
 * 「見出しはロゴだけで、押せるものがない」ことを確かめるテストを置いていた（21647d6）。
 * ピン留めを作ってマークを戻したため（docs/dm.md「DM一覧の見出し」）、そのテストは役目を終え、
 * マークがピン留めの一覧へのリンクであることを確かめるテストに置き換えた（意図した変更）
 */
describe('DM一覧の見出し', () => {
  it('左上のマークは、ピン留めした相談の一覧へのリンクで、ピン留めの件数をバッジに出す', () => {
    const { container } = renderPage(<DmListPage />)

    const header = container.querySelector('header.dm-header') as HTMLElement
    const mark = within(header).getByRole('link', { name: 'ピン留めした相談（3件）' })
    expect(mark.getAttribute('href')).toBe('/dm/pinned')
    expect(within(header).getByRole('heading', { name: 'ISHO' })).toBeTruthy()
    // 通知ベルではない（通知一覧にはつなげない）
    expect(within(header).queryByLabelText(/通知/)).toBeNull()
  })

  it('ピン留めの件数のバッジは、未読のバッジと違う見た目にする（変わらない数字を「対応が必要」と見間違えないように）', () => {
    const { container } = renderPage(<DmListPage />)

    const header = container.querySelector('header.dm-header') as HTMLElement
    const badge = header.querySelector('.dm-pin-badge') as HTMLElement
    expect(badge.textContent).toBe('3')
    expect(badge.classList.contains('dm-count-badge')).toBe(false)
  })

  it('ピン留めがなければバッジを出さない', () => {
    const { container } = renderPage(<DmListPage />, { ...SUMMARY, pinnedCount: 0 })

    const header = container.querySelector('header.dm-header') as HTMLElement
    expect(within(header).getByRole('link', { name: 'ピン留めした相談' })).toBeTruthy()
    expect(header.querySelector('.dm-pin-badge')).toBeNull()
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

describe('DM一覧の見出しのタイトル', () => {
  it('タイトル「ISHO」はロゴの画像で表示し、alt を "ISHO" にする', () => {
    const { container } = renderPage(<DmListPage />)

    const header = container.querySelector('header.dm-header') as HTMLElement
    const heading = within(header).getByRole('heading', { level: 1, name: 'ISHO' })
    const logo = within(heading).getByRole('img')
    expect(logo.getAttribute('alt')).toBe('ISHO')
    expect(logo.getAttribute('src')).toMatch(/logo/)
    // 文字の「ISHO」は残さない（画像だけで見出しになっている）
    expect(heading.textContent).toBe('')
  })
})
