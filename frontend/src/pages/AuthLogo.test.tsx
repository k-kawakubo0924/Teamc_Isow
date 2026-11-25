import { cleanup, render, screen, within } from '@testing-library/react'
import type { ComponentType } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import EmailChangeConfirmPage from './EmailChangeConfirmPage'
import ForgotPasswordPage from './ForgotPasswordPage'
import LoginPage from './LoginPage'
import ResetPasswordPage from './ResetPasswordPage'
import SignUpPage from './SignUpPage'

/*
 * ログインしていなくても見える画面のタイトル「ISHO」は、文字ではなくロゴの画像で出す。
 * 読み上げソフトには今までどおり見出し「ISHO」として伝わるよう、<h1> の中に alt="ISHO" の <img> を置く。
 * ログイン後のヘッダーのタイトルは文字のまま（ここでは扱わない）
 */

vi.mock('../auth/authContext', () => ({ useAuth: () => ({ token: null, isLoggedIn: false, login: vi.fn(), logout: vi.fn() }) }))

afterEach(() => {
  cleanup()
})

const pages: [string, string, ComponentType][] = [
  ['ログイン', '/login', LoginPage],
  ['新規登録', '/register', SignUpPage],
  ['再設定のメールを頼む画面', '/forgot-password', ForgotPasswordPage],
  ['パスワードの再設定（リンクの鍵あり）', '/reset-password#token=abc', ResetPasswordPage],
  ['パスワードの再設定（リンクの鍵なし）', '/reset-password', ResetPasswordPage],
  ['メールアドレスの変更の確認', '/email-change/confirm#token=abc', EmailChangeConfirmPage],
]

describe.each(pages)('%s のタイトル', (_name, path, Page) => {
  it('見出し「ISHO」がロゴの画像で表示され、alt が "ISHO" である', () => {
    render(
      <MemoryRouter initialEntries={[path]}>
        <Page />
      </MemoryRouter>,
    )

    const heading = screen.getByRole('heading', { level: 1, name: 'ISHO' })
    const logo = within(heading).getByRole('img')
    expect(logo.getAttribute('alt')).toBe('ISHO')
    expect(logo.getAttribute('src')).toMatch(/logo/)
    // 文字の「ISHO」は残さない（画像だけで見出しになっている）
    expect(heading.textContent).toBe('')
  })
})
