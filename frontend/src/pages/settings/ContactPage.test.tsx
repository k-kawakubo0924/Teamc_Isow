import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ContactRequest } from '../../api/contacts'
import { ApiError } from '../../api/client'
import ContactPage from './ContactPage'
import SettingsPage from './SettingsPage'

/*
 * お問い合わせ（docs/settings.md「お問い合わせ」、design/Contact us.png）の確認。
 * 返信先メールアドレスは任意（登録済みのアドレスを最初から入れる）。種類と内容は必須で、内容は1000文字まで。
 * 返信はしないため、フォームの上で「現在、個別の返信は行っていません」と伝える
 */

const sendContact = vi.fn<(request: ContactRequest) => Promise<unknown>>()
const me = vi.fn<() => Promise<{ id: number; email: string; phoneNumber: string; username: string }>>()
vi.mock('../../api/contacts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/contacts')>()),
  sendContact: (request: ContactRequest) => sendContact(request),
}))
vi.mock('../../api/auth', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/auth')>()),
  me: () => me(),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true, logout: () => {} }) }))

beforeEach(() => {
  sendContact.mockResolvedValue({ status: 'ACCEPTED' })
  me.mockResolvedValue({ id: 1, email: 'me@example.com', phoneNumber: '09000000000', username: 'me' })
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderPage(path = '/settings/contact') {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/contact" element={<ContactPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

const email = () => screen.getByLabelText(/返信先メールアドレス/) as HTMLInputElement
const category = () => screen.getByLabelText(/お問い合わせの種類/) as HTMLSelectElement
const body = () => screen.getByLabelText(/お問い合わせ内容/) as HTMLTextAreaElement
const submit = () => screen.getByRole('button', { name: '送信' })

describe('お問い合わせ', () => {
  it('詳細設定のメニューから開ける', async () => {
    renderPage('/settings')

    fireEvent.click(screen.getByRole('button', { name: 'お問い合わせ' }))

    expect(await screen.findByRole('heading', { name: 'お問い合わせ' })).toBeTruthy()
  })

  it('返信しないことを伝え、登録済みのアドレスを最初から入れる。アドレスは任意で、種類は4つから選ぶ', async () => {
    const { container } = renderPage()

    expect(container.textContent).toContain('現在、個別の返信は行っていません')
    await waitFor(() => expect(email().value).toBe('me@example.com'))
    expect(email().required).toBe(false)
    expect(container.textContent).toContain('任意')
    expect(container.textContent).toContain('別の連絡先を使いたい場合')
    expect(Array.from(category().options).map((option) => option.textContent)).toEqual([
      '選択してください',
      'アプリについて',
      'ユーザーについて',
      'アカウントについて',
      'その他',
    ])
    expect(container.textContent).toContain('0 / 1000')
  })

  it('種類と内容が未記入なら警告を出し、送らない', async () => {
    renderPage()

    fireEvent.click(submit())

    expect(await screen.findByText('お問い合わせの種類を選択してください')).toBeTruthy()
    expect(screen.getByText('お問い合わせ内容を入力してください')).toBeTruthy()
    expect(sendContact).not.toHaveBeenCalled()
  })

  it('内容が1000文字を超えたら、文字数を赤くし、警告を出して送らない', async () => {
    const { container } = renderPage()
    fireEvent.change(category(), { target: { value: 'APP' } })
    fireEvent.change(body(), { target: { value: 'あ'.repeat(1001) } })

    expect(container.textContent).toContain('1001 / 1000')
    fireEvent.click(submit())

    expect(await screen.findByText('お問い合わせ内容は1000文字以内で入力してください')).toBeTruthy()
    expect(sendContact).not.toHaveBeenCalled()
  })

  it('送ると受け付けた旨を出す。アドレスを空にしても送れる', async () => {
    const { container } = renderPage()
    await waitFor(() => expect(email().value).toBe('me@example.com'))
    fireEvent.change(email(), { target: { value: '' } })
    fireEvent.change(category(), { target: { value: 'ACCOUNT' } })
    fireEvent.change(body(), { target: { value: '  ログインについて\n2行目  ' } })

    fireEvent.click(submit())

    expect(await screen.findByText('お問い合わせを受け付けました。')).toBeTruthy()
    expect(sendContact).toHaveBeenCalledWith({ email: '', category: 'ACCOUNT', body: 'ログインについて\n2行目' })
    expect(container.textContent).toContain('現在、個別の返信は行っていません')
  })

  it('サーバーの入力の誤りは欄の下に、上限などはまとめて出す', async () => {
    // client.ts と同じく、ApiError の message はサーバーの message
    const LIMIT = 'お問い合わせの上限に達しました。時間をおいて再度お試しください。'
    sendContact.mockRejectedValueOnce(
      new ApiError(400, { message: '入力内容に誤りがあります。', errors: { email: 'メールアドレスの形式が正しくありません' } }, '入力内容に誤りがあります。'),
    )
    renderPage()
    fireEvent.change(email(), { target: { value: 'not-an-address' } })
    fireEvent.change(category(), { target: { value: 'OTHER' } })
    fireEvent.change(body(), { target: { value: '内容' } })

    fireEvent.click(submit())
    expect(await screen.findByText('メールアドレスの形式が正しくありません')).toBeTruthy()

    sendContact.mockRejectedValueOnce(
      new ApiError(429, { message: LIMIT, errors: {} }, LIMIT),
    )
    fireEvent.click(submit())
    expect(await screen.findByText(LIMIT)).toBeTruthy()
  })
})
