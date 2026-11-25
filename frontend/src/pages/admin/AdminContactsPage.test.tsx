import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdminContactDetail, AdminContactList, ModerationUserState } from '../../api/admin'
import AdminContactDetailPage from './AdminContactDetailPage'
import AdminContactsPage from './AdminContactsPage'

/*
 * 管理画面のお問い合わせ（docs/admin.md「7. お問い合わせ」）の確認。
 * 一覧には内容・メールアドレスを出さない。詳細は内容を文字として出し、状態（未対応・対応済み・対応不要）とメモを記録する。
 * 利用停止中の申し立てでは、その人の利用停止の状態と解除を同じ画面に出す
 */

type ListParams = { status: string; category: string; channel: string; page: number }
const listContacts = vi.fn<(params: ListParams) => Promise<AdminContactList>>()
const fetchContact = vi.fn<(id: number) => Promise<AdminContactDetail>>()
const changeContactStatus = vi.fn<(id: number, status: string, memo: string) => Promise<unknown>>()
const fetchModerationUser = vi.fn<(id: number) => Promise<ModerationUserState>>()
vi.mock('../../api/admin', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/admin')>()),
  listContacts: (params: ListParams) => listContacts(params),
  fetchContact: (id: number) => fetchContact(id),
  changeContactStatus: (id: number, status: string, memo: string) => changeContactStatus(id, status, memo),
  fetchModerationUser: (id: number) => fetchModerationUser(id),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true }) }))

const LIST: AdminContactList = {
  items: [
    {
      id: 12,
      channel: 'SUSPENDED_APPEAL',
      category: 'ACCOUNT',
      status: 'OPEN',
      userId: 2,
      username: 'haru_st',
      createdAt: '2026-10-12T10:30:00',
      closedAt: null,
    },
    {
      id: 11,
      channel: 'NORMAL',
      category: 'APP',
      status: 'OPEN',
      userId: 3,
      username: null,
      createdAt: '2026-10-11T09:00:00',
      closedAt: null,
    },
  ],
  page: 0,
  size: 20,
  hasNext: false,
}

function detail(overrides: Partial<AdminContactDetail> = {}): AdminContactDetail {
  return {
    id: 11,
    channel: 'NORMAL',
    category: 'APP',
    status: 'OPEN',
    userId: 3,
    username: 'aoi',
    email: 'other@example.com',
    body: '<b>使い方</b>\n2行目',
    createdAt: '2026-10-11T09:00:00',
    closedAt: null,
    handledByUsername: null,
    adminMemo: null,
    ...overrides,
  }
}

const SUSPENDED_USER: ModerationUserState = {
  userId: 2,
  username: 'haru_st',
  admin: false,
  suspended: true,
  suspendedAt: '2026-10-10T09:00:00',
  suspendedUntil: null,
  suspensionReason: 'HARASSMENT',
  suspensionReasonLabel: '嫌がらせ・誹謗中傷',
  history: [],
}

beforeEach(() => {
  listContacts.mockResolvedValue(LIST)
  fetchModerationUser.mockResolvedValue(SUSPENDED_USER)
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/admin/contacts" element={<AdminContactsPage />} />
        <Route path="/admin/contacts/:contactId" element={<AdminContactDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('お問い合わせの一覧', () => {
  it('受付の経路・種類・送った人・日時・状態を出し、既定は未対応だけを読む', async () => {
    renderAt('/admin/contacts')

    await screen.findByText('@haru_st')
    const rows = screen.getAllByRole('row').slice(1)
    expect(within(rows[0]).getByText('利用停止中の申し立て')).toBeTruthy()
    expect(within(rows[0]).getByText('アカウントについて')).toBeTruthy()
    expect(within(rows[0]).getByText('未対応')).toBeTruthy()
    expect(within(rows[0]).getByText('2026/10/12 10:30')).toBeTruthy()
    expect(within(rows[1]).getByText('通常')).toBeTruthy()
    expect(within(rows[1]).getByText('（退会したユーザー）')).toBeTruthy()
    expect(listContacts).toHaveBeenCalledWith({ status: 'OPEN', category: '', channel: '', page: 0 })
  })

  it('状態・種類・受付の経路で絞り込める', async () => {
    renderAt('/admin/contacts')
    await screen.findByText('@haru_st')

    fireEvent.change(screen.getByLabelText('受付'), { target: { value: 'SUSPENDED_APPEAL' } })
    await waitFor(() =>
      expect(listContacts).toHaveBeenLastCalledWith({ status: 'OPEN', category: '', channel: 'SUSPENDED_APPEAL', page: 0 }),
    )
    fireEvent.change(screen.getByLabelText('種類'), { target: { value: 'USER' } })
    fireEvent.change(screen.getByLabelText('状態'), { target: { value: '' } })
    await waitFor(() =>
      expect(listContacts).toHaveBeenLastCalledWith({ status: '', category: 'USER', channel: 'SUSPENDED_APPEAL', page: 0 }),
    )
  })
})

describe('お問い合わせの詳細', () => {
  it('内容は HTML として扱わず、改行はそのまま出す。別の連絡先も出す', async () => {
    fetchContact.mockResolvedValue(detail())
    const { container } = renderAt('/admin/contacts/11')

    const body = await screen.findByText(/使い方/)
    expect(body.textContent).toBe('<b>使い方</b>\n2行目')
    expect(container.querySelector('b')).toBeNull()
    expect(container.textContent).toContain('other@example.com')
    expect(container.textContent).toContain('@aoi')
    // 通常のお問い合わせでは、利用停止の欄を出さない
    expect(fetchModerationUser).not.toHaveBeenCalled()
  })

  it('別の連絡先がなければ、登録済みのアドレスを使う旨を出す（アドレスそのものは出さない）', async () => {
    fetchContact.mockResolvedValue(detail({ email: null }))
    const { container } = renderAt('/admin/contacts/11')

    await screen.findByText(/使い方/)
    expect(container.textContent).toContain('入力なし')
  })

  it('対応済みにするとメモと一緒に記録し、未対応に戻すボタンに変わる', async () => {
    fetchContact.mockResolvedValue(detail())
    changeContactStatus.mockResolvedValue({
      id: 11,
      status: 'RESOLVED',
      closedAt: '2026-10-12T11:00:00',
      handledByUsername: 'admin',
      adminMemo: '案内した',
    })
    renderAt('/admin/contacts/11')
    await screen.findByText(/使い方/)

    fireEvent.change(screen.getByLabelText(/メモ/), { target: { value: '案内した' } })
    fireEvent.click(screen.getByRole('button', { name: '対応済みにする' }))

    expect(await screen.findByRole('button', { name: '未対応に戻す' })).toBeTruthy()
    expect(changeContactStatus).toHaveBeenCalledWith(11, 'RESOLVED', '案内した')
    expect(screen.getByText('対応済み')).toBeTruthy()
    expect(screen.getByRole('button', { name: '未対応に戻す' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: '対応不要にする' })).toBeNull()
  })

  it('利用停止中の申し立てでは、その人の利用停止の状態と解除を出す', async () => {
    fetchContact.mockResolvedValue(detail({ id: 12, channel: 'SUSPENDED_APPEAL', category: 'ACCOUNT', userId: 2, username: 'haru_st' }))
    const { container } = renderAt('/admin/contacts/12')

    await screen.findByText(/使い方/)
    expect(container.textContent).toContain('利用停止中の申し立て')
    await screen.findByRole('button', { name: /利用停止を解除/ })
    expect(fetchModerationUser).toHaveBeenCalledWith(2)
    expect(container.textContent).toContain('嫌がらせ・誹謗中傷')
  })
})
