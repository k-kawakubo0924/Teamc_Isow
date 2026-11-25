import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import ForgotPasswordPage from './ForgotPasswordPage'
import LoginPage from './LoginPage'
import ResetPasswordPage from './ResetPasswordPage'

/*
 * パスワードの再設定（ログイン前。docs/auth.md「パスワードの再設定」）の画面の確認。
 * サーバーは登録の有無にかかわらず同じ文言を返すため、画面もその文言をそのまま出す。
 * リンクの鍵は URL の # の後ろにあり、読んだら URL から消す
 */

const requestPasswordReset = vi.fn<(email: string) => Promise<{ message: string }>>()
const confirmPasswordReset = vi.fn<(token: string, newPassword: string) => Promise<void>>()
vi.mock('../api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/auth')>()),
  requestPasswordReset: (email: string) => requestPasswordReset(email),
  confirmPasswordReset: (token: string, newPassword: string) => confirmPasswordReset(token, newPassword),
}))
const logout = vi.fn()
vi.mock('../auth/authContext', () => ({ useAuth: () => ({ token: null, isLoggedIn: false, login: vi.fn(), logout }) }))

const ACCEPTED =
  '入力したメールアドレスが登録されていれば、パスワード再設定のメールを送りました。届かない場合は、時間をおいてもう一度お試しください。'

beforeEach(() => {
  requestPasswordReset.mockResolvedValue({ message: ACCEPTED })
  confirmPasswordReset.mockResolvedValue(undefined)
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
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Routes>
      <Where />
    </MemoryRouter>,
  )
}

describe('ログイン画面の「パスワードをお忘れですか？」', () => {
  it('再設定のメールを頼む画面へのリンクになっている（「準備中」は出さない）', () => {
    const { container } = renderAt('/login')

    const link = screen.getByRole('link', { name: 'パスワードをお忘れですか？' })
    expect(link.getAttribute('href')).toBe('/forgot-password')
    expect(container.textContent).not.toContain('準備中')
  })
})

describe('再設定のメールを頼む画面', () => {
  it('メールアドレスをそろえて送り、サーバーの文言（登録の有無で変わらない）をそのまま出す', async () => {
    renderAt('/forgot-password')

    fireEvent.change(screen.getByLabelText('メールアドレス'), { target: { value: '  User@Example.com ' } })
    fireEvent.click(screen.getByRole('button', { name: '再設定のメールを送る' }))

    expect(await screen.findByText(ACCEPTED)).toBeTruthy()
    expect(requestPasswordReset).toHaveBeenCalledWith('user@example.com')
  })

  it('空・形式の誤りは送らずに欄の下に出す', async () => {
    renderAt('/forgot-password')

    fireEvent.click(screen.getByRole('button', { name: '再設定のメールを送る' }))
    expect(await screen.findByText('メールアドレスを入力してください')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('メールアドレス'), { target: { value: 'not-an-address' } })
    fireEvent.click(screen.getByRole('button', { name: '再設定のメールを送る' }))
    expect(await screen.findByText('メールアドレスの形式が正しくありません')).toBeTruthy()
    expect(requestPasswordReset).not.toHaveBeenCalled()
  })

  it('ログイン画面へ戻れる', () => {
    renderAt('/forgot-password')

    expect(screen.getByRole('link', { name: 'ログイン画面に戻る' }).getAttribute('href')).toBe('/login')
  })
})

describe('新しいパスワードを設定する画面', () => {
  it('リンクの鍵を読んだら URL から消す', async () => {
    renderAt('/reset-password#token=abc.def.ghi')

    expect(await screen.findByRole('heading', { name: 'パスワードの再設定' })).toBeTruthy()
    expect(screen.getByTestId('where').textContent).toBe('/reset-password')
  })

  it('規則に合わない・確認と違うときは送らない', async () => {
    renderAt('/reset-password#token=abc.def.ghi')

    fireEvent.change(screen.getByLabelText('新しいパスワード'), { target: { value: 'short' } })
    fireEvent.change(screen.getByLabelText('確認用パスワード'), { target: { value: 'short' } })
    fireEvent.click(screen.getByRole('button', { name: 'パスワードを再設定する' }))
    expect(await screen.findByText('8文字以上、英字と数字を含めてください')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('新しいパスワード'), { target: { value: 'newpass456' } })
    fireEvent.change(screen.getByLabelText('確認用パスワード'), { target: { value: 'newpass457' } })
    fireEvent.click(screen.getByRole('button', { name: 'パスワードを再設定する' }))
    expect(await screen.findByText('確認用パスワードが一致しません')).toBeTruthy()
    expect(confirmPasswordReset).not.toHaveBeenCalled()
  })

  it('設定したら、この端末もログアウトしてログイン画面へ移り、再設定したことを出す', async () => {
    renderAt('/reset-password#token=abc.def.ghi')

    fireEvent.change(screen.getByLabelText('新しいパスワード'), { target: { value: 'newpass456' } })
    fireEvent.change(screen.getByLabelText('確認用パスワード'), { target: { value: 'newpass456' } })
    fireEvent.click(screen.getByRole('button', { name: 'パスワードを再設定する' }))

    expect(await screen.findByText('パスワードを再設定しました。新しいパスワードでログインしてください。')).toBeTruthy()
    expect(confirmPasswordReset).toHaveBeenCalledWith('abc.def.ghi', 'newpass456')
    expect(logout).toHaveBeenCalled()
    expect(screen.getByTestId('where').textContent).toBe('/login')
  })

  it('鍵が使えないときは、サーバーの文言と、もう一度頼む画面へのリンクを出す', async () => {
    const message = 'このリンクは使えません。有効期限（30分）が切れたか、すでに使われています。もう一度、再設定のメールを送ってください。'
    confirmPasswordReset.mockRejectedValue(new ApiError(400, { message, errors: {} }, message))
    renderAt('/reset-password#token=old')

    fireEvent.change(screen.getByLabelText('新しいパスワード'), { target: { value: 'newpass456' } })
    fireEvent.change(screen.getByLabelText('確認用パスワード'), { target: { value: 'newpass456' } })
    fireEvent.click(screen.getByRole('button', { name: 'パスワードを再設定する' }))

    expect(await screen.findByText(message)).toBeTruthy()
    expect(screen.getByRole('link', { name: '再設定のメールをもう一度送る' }).getAttribute('href')).toBe('/forgot-password')
  })

  it('鍵のないリンクで開いたら、入力欄を出さずに、もう一度頼む画面へ案内する', () => {
    renderAt('/reset-password')

    expect(screen.queryByLabelText('新しいパスワード')).toBeNull()
    expect(screen.getByRole('link', { name: '再設定のメールをもう一度送る' })).toBeTruthy()
  })
})
