import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { useEffect } from 'react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { getJson, postJson } from '../api/client'
import SuspendedPage from '../pages/SuspendedPage'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './authContext'
import { GuestOnly, RequireAuth } from './RouteGuards'
import { getSession, saveToken } from './tokenStorage'

/*
 * 利用停止（docs/admin.md「6. 措置」）の画面側の確認。
 * サーバーが 403（reason: ACCOUNT_SUSPENDED）を返したら、どの画面からでもログアウトして利用停止の画面を出す。
 * ログインのときも同じ（パスワードが正しく、利用停止中のとき）
 */

const SUSPENDED = {
  message: 'このアカウントは利用停止中です。',
  errors: {},
  reason: 'ACCOUNT_SUSPENDED',
  suspendedUntil: '2026-10-22T10:00:00',
  suspensionReason: 'HARASSMENT',
  suspensionReasonLabel: '嫌がらせ・誹謗中傷',
}

let response: { status: number; body: unknown }

beforeEach(() => {
  localStorage.clear()
  response = { status: 403, body: SUSPENDED }
  vi.stubGlobal(
    'fetch',
    vi.fn(async () =>
      new Response(JSON.stringify(response.body), {
        status: response.status,
        headers: { 'Content-Type': 'application/json' },
      }),
    ),
  )
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  localStorage.clear()
})

/** ログイン中の画面。開いたら API を呼ぶ */
function HomeProbe() {
  const { token } = useAuth()
  useEffect(() => {
    getJson('/api/users/me', token).catch(() => {})
  }, [token])
  return <p>ホーム</p>
}

/** ログイン画面。開いたらログインの API を呼ぶ */
function LoginProbe() {
  useEffect(() => {
    postJson('/api/auth/login', { email: 'a@example.com', password: 'password123' }).catch(() => {})
  }, [])
  return <p>ログイン画面</p>
}

function Where() {
  return <p data-testid="where">{useLocation().pathname}</p>
}

function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route element={<RequireAuth />}>
            <Route path="/" element={<HomeProbe />} />
          </Route>
          <Route element={<GuestOnly />}>
            <Route path="/login" element={<LoginProbe />} />
          </Route>
          <Route path="/suspended" element={<SuspendedPage />} />
        </Routes>
        <Where />
      </AuthProvider>
    </MemoryRouter>,
  )
}

function logIn() {
  saveToken('token', new Date(Date.now() + 60 * 60 * 1000).toISOString())
}

describe('利用停止', () => {
  it('ログイン中に API が利用停止を返したら、ログアウトして、理由と期限と問い合わせ先を出す', async () => {
    logIn()
    const { container } = renderApp('/')

    await screen.findByRole('heading', { name: 'アカウントが利用停止になっています' })
    expect(screen.getByTestId('where').textContent).toBe('/suspended')
    expect(container.textContent).toContain('嫌がらせ・誹謗中傷')
    expect(container.textContent).toContain('2026/10/22 10:00 まで')
    expect(container.textContent).toContain('お問い合わせ（準備中）')
    expect(getSession()).toBeNull()
  })

  it('期限がなければ「無期限」と出す', async () => {
    response = { status: 403, body: { ...SUSPENDED, suspendedUntil: null } }
    logIn()
    const { container } = renderApp('/')

    await screen.findByRole('heading', { name: 'アカウントが利用停止になっています' })
    expect(container.textContent).toContain('無期限')
    expect(container.textContent).not.toMatch(/null|undefined/)
  })

  it('ログインで利用停止を返したときも、利用停止の画面を出す', async () => {
    renderApp('/login')

    await screen.findByRole('heading', { name: 'アカウントが利用停止になっています' })
    expect(getSession()).toBeNull()
  })

  it('「ログイン画面に戻る」でログイン画面へ戻る', async () => {
    logIn()
    renderApp('/')
    await screen.findByRole('heading', { name: 'アカウントが利用停止になっています' })
    // 戻った先のログイン画面がまたログインの API を呼ぶため、利用停止ではない応答にしておく
    response = { status: 401, body: { message: 'x', errors: {} } }

    fireEvent.click(screen.getByRole('button', { name: 'ログイン画面に戻る' }))

    expect((await screen.findByText('ログイン画面')).textContent).toBe('ログイン画面')
  })

  it('利用停止ではない 403 では、ログアウトしない', async () => {
    response = { status: 403, body: { message: '権限がありません', errors: {} } }
    logIn()
    renderApp('/')

    await screen.findByText('ホーム')
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(screen.getByTestId('where').textContent).toBe('/')
    expect(getSession()).not.toBeNull()
  })

  it('利用停止の情報がないまま /suspended を開いたら、ログイン画面へ移る', async () => {
    response = { status: 401, body: { message: 'x', errors: {} } }
    renderApp('/suspended')

    expect((await screen.findByText('ログイン画面')).textContent).toBe('ログイン画面')
  })
})
