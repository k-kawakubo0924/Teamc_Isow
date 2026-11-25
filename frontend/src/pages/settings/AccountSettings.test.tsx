import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import EmailChangeConfirmPage from '../EmailChangeConfirmPage'
import LoginPage from '../LoginPage'
import EmailSettingsPage from './EmailSettingsPage'
import PasswordSettingsPage from './PasswordSettingsPage'
import SettingsPage from './SettingsPage'

/*
 * ログイン中のパスワードの変更・メールアドレスの変更（docs/settings.md）の画面の確認。
 * どちらも現在のパスワードを求める（メールアドレスの変更では設計画像にない欄を足した）
 */

const changePassword = vi.fn<(current: string, next: string) => Promise<{ token: string; expiresAt: string }>>()
const requestEmailChange = vi.fn<(current: string, newEmail: string, confirmEmail: string) => Promise<{ message: string }>>()
const confirmEmailChange = vi.fn<(token: string) => Promise<void>>()
vi.mock('../../api/account', () => ({
  changePassword: (current: string, next: string) => changePassword(current, next),
  requestEmailChange: (current: string, newEmail: string, confirmEmail: string) =>
    requestEmailChange(current, newEmail, confirmEmail),
  confirmEmailChange: (token: string) => confirmEmailChange(token),
}))
const me = vi.fn<() => Promise<{ id: number; email: string; phoneNumber: string; username: string }>>()
vi.mock('../../api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/auth')>()),
  me: () => me(),
}))
const login = vi.fn()
const logout = vi.fn()
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true, login, logout }) }))

const ACCEPTED =
  '確認メールを送りました。新しいメールアドレスに届いたリンクを開くと、変更が完了します。完了するまでは、今のメールアドレスでログインできます。'

beforeEach(() => {
  me.mockResolvedValue({ id: 1, email: 'yuu.style@example.com', phoneNumber: '09000000000', username: 'yuu' })
  changePassword.mockResolvedValue({ token: 'new-token', expiresAt: '2026-10-17T10:00:00Z' })
  requestEmailChange.mockResolvedValue({ message: ACCEPTED })
  confirmEmailChange.mockResolvedValue(undefined)
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function Where() {
  const location = useLocation()
  return <p data-testid="where">{location.pathname + location.hash}</p>
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/password" element={<PasswordSettingsPage />} />
        <Route path="/settings/email" element={<EmailSettingsPage />} />
        <Route path="/email-change/confirm" element={<EmailChangeConfirmPage />} />
      </Routes>
      <Where />
    </MemoryRouter>,
  )
}

describe('詳細設定のメニュー', () => {
  it('パスワード再設定・メールアドレス再設定はそれぞれの画面へ移る（「準備中」は出さない）', async () => {
    const { container } = renderAt('/settings')

    fireEvent.click(screen.getByRole('button', { name: 'パスワード再設定' }))
    expect(screen.getByTestId('where').textContent).toBe('/settings/password')
    expect(container.textContent).not.toContain('準備中')
  })
})

describe('パスワード再設定（ログイン中の変更）', () => {
  it('現在・新しい・確認用の3つを入れて「完了」で変え、この端末の新しいトークンを保存して詳細設定に戻る', async () => {
    renderAt('/settings/password')

    fireEvent.change(screen.getByLabelText('現在のパスワード'), { target: { value: 'password123' } })
    fireEvent.change(screen.getByLabelText('新しいパスワード'), { target: { value: 'newpass456' } })
    fireEvent.change(screen.getByLabelText('確認用パスワード'), { target: { value: 'newpass456' } })
    fireEvent.click(screen.getByRole('button', { name: '完了' }))

    await waitFor(() => expect(screen.getByTestId('where').textContent).toBe('/settings'))
    expect(changePassword).toHaveBeenCalledWith('password123', 'newpass456')
    expect(login).toHaveBeenCalledWith('new-token', '2026-10-17T10:00:00Z')
    expect(await screen.findByText('パスワードを変更しました。ほかの端末はログアウトしました。')).toBeTruthy()
  })

  it('規則・確認との違いは送らずに欄の下に出し、サーバーの誤り（現在のパスワードが違う）も欄の下に出す', async () => {
    renderAt('/settings/password')

    fireEvent.change(screen.getByLabelText('現在のパスワード'), { target: { value: 'password123' } })
    fireEvent.change(screen.getByLabelText('新しいパスワード'), { target: { value: 'newpass456' } })
    fireEvent.change(screen.getByLabelText('確認用パスワード'), { target: { value: 'newpass457' } })
    fireEvent.click(screen.getByRole('button', { name: '完了' }))
    expect(await screen.findByText('確認用パスワードが一致しません')).toBeTruthy()
    expect(changePassword).not.toHaveBeenCalled()

    changePassword.mockRejectedValueOnce(
      new ApiError(400, { message: '入力内容に誤りがあります。', errors: { currentPassword: '現在のパスワードが正しくありません' } }, 'x'),
    )
    fireEvent.change(screen.getByLabelText('確認用パスワード'), { target: { value: 'newpass456' } })
    fireEvent.click(screen.getByRole('button', { name: '完了' }))
    expect(await screen.findByText('現在のパスワードが正しくありません')).toBeTruthy()
    expect(login).not.toHaveBeenCalled()
  })
})

describe('メールアドレス再設定', () => {
  it('今のアドレスを出し、現在のパスワードの欄がある（設計画像にない欄）', async () => {
    renderAt('/settings/email')

    expect(await screen.findByText('yuu.style@example.com')).toBeTruthy()
    expect(screen.getByLabelText('現在のパスワード')).toBeTruthy()
    expect(screen.getByText('入力したアドレスに確認メールを送ります')).toBeTruthy()
  })

  it('「完了」で申し込み、サーバーの文言（使われているアドレスでも同じ）を出す', async () => {
    renderAt('/settings/email')
    await screen.findByText('yuu.style@example.com')

    fireEvent.change(screen.getByLabelText('新しいメールアドレス'), { target: { value: ' New@Example.com' } })
    fireEvent.change(screen.getByLabelText('確認用メールアドレス'), { target: { value: 'new@example.com' } })
    fireEvent.change(screen.getByLabelText('現在のパスワード'), { target: { value: 'password123' } })
    fireEvent.click(screen.getByRole('button', { name: '完了' }))

    expect(await screen.findByText(ACCEPTED)).toBeTruthy()
    expect(requestEmailChange).toHaveBeenCalledWith('password123', 'new@example.com', 'new@example.com')
  })

  it('確認用が違う・未入力は送らない', async () => {
    renderAt('/settings/email')
    await screen.findByText('yuu.style@example.com')

    fireEvent.change(screen.getByLabelText('新しいメールアドレス'), { target: { value: 'new@example.com' } })
    fireEvent.change(screen.getByLabelText('確認用メールアドレス'), { target: { value: 'other@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: '完了' }))

    expect(await screen.findByText('確認用メールアドレスが一致しません')).toBeTruthy()
    expect(screen.getByText('現在のパスワードを入力してください')).toBeTruthy()
    expect(requestEmailChange).not.toHaveBeenCalled()
  })
})

describe('メールアドレスの変更の確認（メールのリンク）', () => {
  it('鍵を URL から消し、開いただけでは完了しない。押すと完了し、ログアウトしてログイン画面へ移る', async () => {
    renderAt('/email-change/confirm#token=abc.def')

    expect(await screen.findByRole('button', { name: 'メールアドレスの変更を完了する' })).toBeTruthy()
    expect(screen.getByTestId('where').textContent).toBe('/email-change/confirm')
    expect(confirmEmailChange).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'メールアドレスの変更を完了する' }))

    expect(await screen.findByText('メールアドレスを変更しました。新しいメールアドレスでログインしてください。')).toBeTruthy()
    expect(confirmEmailChange).toHaveBeenCalledWith('abc.def')
    expect(logout).toHaveBeenCalled()
  })

  it('鍵が使えなければサーバーの文言を出す', async () => {
    const message = 'このリンクは使えません。有効期限（24時間）が切れたか、すでに使われているか、新しい手続きで無効になりました。もう一度、変更の手続きをしてください。'
    confirmEmailChange.mockRejectedValue(new ApiError(400, { message, errors: {} }, message))
    renderAt('/email-change/confirm#token=old')

    fireEvent.click(await screen.findByRole('button', { name: 'メールアドレスの変更を完了する' }))

    expect(await screen.findByText(message)).toBeTruthy()
  })
})
