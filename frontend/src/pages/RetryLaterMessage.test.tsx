import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import LoginPage from './LoginPage'
import EmailSettingsPage from './settings/EmailSettingsPage'
import PasswordSettingsPage from './settings/PasswordSettingsPage'

/*
 * 失敗が続いて止まったとき（429。docs/auth.md「ログインの失敗の回数の上限」）の文言が、利用者の目に入る場所に出ることの確認。
 * - ふだんエラーが出ている場所と同じところに出る（ログイン：上の警告文。設定の2画面：フォームの上のエラー）
 * - その場所へ画面を動かす（設定の画面は「完了」が上に貼り付いたヘッダーにあり、下の欄まで動かしたまま押せるため、
 *   動かさないと文言が画面の外に出たままになる。ログイン画面も、スマホでキーボードが出ていると上の警告文が隠れる）
 * jsdom は配置を計算しないため、「同じ要素・同じ位置（入力欄より前）に出る」と「その要素へ動かした」で確かめる
 */

const login = vi.fn()
vi.mock('../api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/auth')>()),
  login: (request: unknown) => login(request),
  me: () => Promise.resolve({ id: 1, email: 'yuu.style@example.com', phoneNumber: '09000000000', username: 'yuu' }),
}))
const changePassword = vi.fn()
const requestEmailChange = vi.fn()
vi.mock('../api/account', () => ({
  changePassword: (...args: unknown[]) => changePassword(...args),
  requestEmailChange: (...args: unknown[]) => requestEmailChange(...args),
  confirmEmailChange: vi.fn(),
}))
vi.mock('../auth/authContext', () => ({
  useAuth: () => ({ token: 'token', isLoggedIn: true, login: vi.fn(), logout: vi.fn() }),
}))

const LOGIN_LIMIT =
  'ログインの失敗が続いたため、一時的に受け付けを止めています。あと約15分で、もう一度お試しください。パスワードを忘れた場合は「パスワードをお忘れですか？」から再設定できます。'
const CURRENT_PASSWORD_LIMIT =
  '現在のパスワードの誤りが続いたため、一時的に受け付けを止めています。あと約15分で、もう一度お試しください。'
const CONNECTION_ERROR = 'サーバーに接続できませんでした。時間をおいて再度お試しください。'

function limited(message: string) {
  return new ApiError(429, { message, errors: {} }, message)
}

/** 画面を動かした要素（scrollIntoView を呼ばれた要素）を記録する */
let scrolled: Element[] = []
const originalScrollIntoView = Element.prototype.scrollIntoView

beforeEach(() => {
  scrolled = []
  Element.prototype.scrollIntoView = function (this: Element) {
    scrolled.push(this)
  }
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  Element.prototype.scrollIntoView = originalScrollIntoView
})

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/settings/password" element={<PasswordSettingsPage />} />
        <Route path="/settings/email" element={<EmailSettingsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

/** 要素がどこに出ているか（親と、その中で何番目か） */
function placeOf(element: Element) {
  const parent = element.parentElement
  return { parent, index: parent ? Array.from(parent.children).indexOf(element) : -1 }
}

function isBefore(a: Element, b: Element) {
  return (a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
}

describe('ログイン画面', () => {
  function submit(email: string, password: string) {
    fireEvent.change(screen.getByLabelText('メールアドレス'), { target: { value: email } })
    fireEvent.change(screen.getByLabelText('パスワード'), { target: { value: password } })
    fireEvent.click(screen.getByRole('button', { name: 'ログイン' }))
  }

  it('止まったときの文言は、認証の失敗と同じ上の警告文に出て、そこへ画面を動かす', async () => {
    renderAt('/login')

    // ふだんのエラー（認証の失敗）が出る場所
    login.mockRejectedValueOnce(
      new ApiError(401, { message: 'メールアドレスまたはパスワードが正しくありません。入力内容をご確認ください。', errors: {} }, 'メールアドレスまたはパスワードが正しくありません。入力内容をご確認ください。'),
    )
    submit('yuu.style@example.com', 'wrongpass999')
    const usual = placeOf(await screen.findByRole('alert'))

    login.mockRejectedValueOnce(limited(LOGIN_LIMIT))
    submit('yuu.style@example.com', 'password123')
    await screen.findByText(LOGIN_LIMIT)
    const alert = screen.getByRole('alert')

    expect(alert.textContent).toContain(LOGIN_LIMIT)
    expect(placeOf(alert)).toEqual(usual)
    expect(isBefore(alert, screen.getByLabelText('メールアドレス'))).toBe(true)
    expect(scrolled).toContain(alert)
    // 止まっただけで、入力が違うとは限らないので、欄は赤くしない
    expect(screen.getByLabelText('メールアドレス').getAttribute('aria-invalid')).toBeNull()
    expect(screen.getByLabelText('パスワード').getAttribute('aria-invalid')).toBeNull()
  })
})

describe('パスワード再設定（ログイン中の変更）', () => {
  function submit(current: string) {
    fireEvent.change(screen.getByLabelText('現在のパスワード'), { target: { value: current } })
    fireEvent.change(screen.getByLabelText('新しいパスワード'), { target: { value: 'newpass456' } })
    fireEvent.change(screen.getByLabelText('確認用パスワード'), { target: { value: 'newpass456' } })
    fireEvent.click(screen.getByRole('button', { name: '完了' }))
  }

  it('止まったときの文言は、フォームの上のエラーと同じところに出て、そこへ画面を動かす（欄の下には出さない）', async () => {
    renderAt('/settings/password')

    // ふだんのエラー（欄に結びつかない失敗）が出る場所
    changePassword.mockRejectedValueOnce(new ApiError(0, null, CONNECTION_ERROR))
    submit('password123')
    const usual = placeOf(await screen.findByRole('alert'))

    changePassword.mockRejectedValueOnce(limited(CURRENT_PASSWORD_LIMIT))
    submit('password123')
    await screen.findByText(CURRENT_PASSWORD_LIMIT)
    const alert = screen.getByRole('alert')

    expect(alert.textContent).toBe(CURRENT_PASSWORD_LIMIT)
    expect(placeOf(alert)).toEqual(usual)
    expect(isBefore(alert, screen.getByLabelText('現在のパスワード'))).toBe(true)
    await waitFor(() => expect(scrolled).toContain(alert))
    expect(screen.getByLabelText('現在のパスワード').getAttribute('aria-invalid')).toBeNull()
  })
})

describe('メールアドレス再設定', () => {
  async function submit(current: string) {
    fireEvent.change(screen.getByLabelText('新しいメールアドレス'), { target: { value: 'new@example.com' } })
    fireEvent.change(screen.getByLabelText('確認用メールアドレス'), { target: { value: 'new@example.com' } })
    fireEvent.change(screen.getByLabelText('現在のパスワード'), { target: { value: current } })
    fireEvent.click(screen.getByRole('button', { name: '完了' }))
  }

  it('止まったときの文言は、フォームの上のエラーと同じところに出て、そこへ画面を動かす（欄の下には出さない）', async () => {
    renderAt('/settings/email')
    await screen.findByText('yuu.style@example.com')

    requestEmailChange.mockRejectedValueOnce(new ApiError(0, null, CONNECTION_ERROR))
    await submit('password123')
    const usual = placeOf(await screen.findByRole('alert'))

    requestEmailChange.mockRejectedValueOnce(limited(CURRENT_PASSWORD_LIMIT))
    await submit('password123')
    await screen.findByText(CURRENT_PASSWORD_LIMIT)
    const alert = screen.getByRole('alert')

    expect(alert.textContent).toBe(CURRENT_PASSWORD_LIMIT)
    expect(placeOf(alert)).toEqual(usual)
    expect(isBefore(alert, screen.getByLabelText('新しいメールアドレス'))).toBe(true)
    await waitFor(() => expect(scrolled).toContain(alert))
    expect(screen.getByLabelText('現在のパスワード').getAttribute('aria-invalid')).toBeNull()
  })
})
