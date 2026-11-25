import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminLayout from './AdminLayout'

/*
 * 管理画面の枠の左上のタイトル。「ISHO」の部分はロゴの画像で出し、「管理画面」は文字のまま続ける。
 * 読み上げソフトには今までどおり「ISHO 管理画面」と伝わるよう、画像の alt を "ISHO" にする
 */

vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true }) }))
vi.mock('../../api/admin', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/admin')>()),
  getAdminMe: () => Promise.resolve({ username: 'admin_user' }),
}))

afterEach(() => {
  cleanup()
})

describe('管理画面の枠のタイトル', () => {
  it('「ISHO」はロゴの画像（alt="ISHO"）で、「管理画面」は文字で表示する', async () => {
    const { container } = render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/admin" element={<AdminLayout />} />
        </Routes>
      </MemoryRouter>,
    )

    await screen.findByText('admin_user')
    const title = container.querySelector('.admin-logo') as HTMLElement
    const logo = within(title).getByRole('img')
    expect(logo.getAttribute('alt')).toBe('ISHO')
    expect(logo.getAttribute('src')).toMatch(/logo/)
    // 文字の「ISHO」は残さない
    expect(title.textContent?.trim()).toBe('管理画面')
  })
})
