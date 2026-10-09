import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminAnnouncementsPage from './AdminAnnouncementsPage'

/*
 * お知らせの画面（docs/admin.md「お知らせの発行」）の確認。
 * fetch を、管理 API と同じ形で答える偽のサーバーに置き換える
 */

vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true }) }))

type Announcement = {
  id: number
  title: string
  body: string
  publishedAt: string
  publisherId: number
  publisherUsername: string
}

/** サーバーにあるお知らせ（新しい順） */
let stored: Announcement[]
let requests: { method: string; path: string; query: string; body: unknown }[]
let holdNext: Promise<void> | null
/** 次の発行をサーバーで弾く（画面の確認を通り抜けた入力の誤り） */
let rejectNext: Record<string, string> | null

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

async function fakeFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const url = new URL(String(input))
  const method = init?.method ?? 'GET'
  const body = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
  requests.push({ method, path: url.pathname, query: url.search, body })
  if (holdNext) {
    const hold = holdNext
    holdNext = null
    await hold
  }
  if (url.pathname !== '/api/admin/announcements') return json(404, null)
  if (method === 'GET') {
    const page = Number(url.searchParams.get('page'))
    const size = Number(url.searchParams.get('size'))
    const items = stored.slice(page * size, page * size + size)
    return json(200, { items, page, size, hasNext: stored.length > (page + 1) * size })
  }
  if (rejectNext) {
    const errors = rejectNext
    rejectNext = null
    return json(400, { message: '入力内容に誤りがあります。赤い欄を修正してください。', errors })
  }
  const created = {
    id: 100 + requests.length,
    title: body.title,
    body: body.body,
    publishedAt: '2026-10-09T15:30:00.123',
    publisherId: 1,
    publisherUsername: 'admin',
  }
  stored.unshift(created)
  return json(201, created)
}

beforeEach(() => {
  stored = [
    {
      id: 2,
      title: 'メンテナンスのお知らせ',
      body: '10月10日 2:00〜4:00 にメンテナンスを行います。\nご不便をおかけします。',
      publishedAt: '2026-10-08T09:05:00',
      publisherId: 1,
      publisherUsername: 'admin',
    },
    {
      id: 1,
      title: 'サービス開始',
      body: '<b>ISHO</b> を始めました',
      publishedAt: '2026-10-01T18:00:00',
      publisherId: 1,
      publisherUsername: 'old_admin',
    },
  ]
  requests = []
  holdNext = null
  rejectNext = null
  vi.stubGlobal('fetch', vi.fn(fakeFetch))
  vi.stubGlobal('confirm', vi.fn(() => true))
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminAnnouncementsPage />
    </MemoryRouter>,
  )
}

function fill(title: string, body: string) {
  fireEvent.change(screen.getByRole('textbox', { name: '題名' }), { target: { value: title } })
  fireEvent.change(screen.getByRole('textbox', { name: '本文' }), { target: { value: body } })
}

function items(): HTMLElement[] {
  return screen.getAllByRole('article')
}

function posts() {
  return requests.filter((r) => r.method === 'POST')
}

describe('発行済みの一覧', () => {
  it('新しい順に、題名・発行日時・発行者・本文を出す', async () => {
    renderPage()

    await screen.findByText('メンテナンスのお知らせ')
    const [first, second] = items()
    expect(within(first).getByRole('heading', { name: 'メンテナンスのお知らせ' })).toBeTruthy()
    expect(first.textContent).toContain('2026/10/08 09:05')
    expect(first.textContent).toContain('admin')
    expect(within(second).getByRole('heading', { name: 'サービス開始' })).toBeTruthy()
    expect(second.textContent).toContain('old_admin')
    expect(requests[0]).toMatchObject({ method: 'GET', path: '/api/admin/announcements' })
  })

  it('本文の改行はそのまま出し、HTML としては扱わない', async () => {
    const { container } = renderPage()
    await screen.findByText('メンテナンスのお知らせ')

    const body = items()[0].querySelector('.admin-announcements-body') as HTMLElement
    expect(body.textContent).toBe('10月10日 2:00〜4:00 にメンテナンスを行います。\nご不便をおかけします。')
    const html = items()[1].querySelector('.admin-announcements-body') as HTMLElement
    expect(html.textContent).toBe('<b>ISHO</b> を始めました')
    expect(container.querySelector('.admin-announcements-body b')).toBeNull()
  })

  it('まだ発行していなければ、そのことを出す', async () => {
    stored = []
    renderPage()

    expect(await screen.findByText('まだお知らせを発行していません。')).toBeTruthy()
  })

  it('続きがあれば「もっと見る」で古いものを後ろに足す', async () => {
    stored = Array.from({ length: 25 }, (_, i) => ({
      ...stored[0],
      id: 100 - i,
      title: `お知らせ${i + 1}`,
    }))
    renderPage()
    await screen.findByText('お知らせ1')
    expect(items()).toHaveLength(20)

    fireEvent.click(screen.getByRole('button', { name: 'もっと見る' }))

    await screen.findByText('お知らせ25')
    expect(items()).toHaveLength(25)
    expect(screen.queryByRole('button', { name: 'もっと見る' })).toBeNull()
    expect(requests.map((r) => r.query)).toEqual(['?page=0&size=20', '?page=1&size=20'])
  })

  it('読み込めなかったときは、エラーを画面に出す', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(500, null)))
    renderPage()

    expect(await screen.findByRole('alert')).toBeTruthy()
  })
})

describe('発行', () => {
  it('発行する前に画面の中で確かめ、利用者側の表示ができたら取り消せないことを書く', async () => {
    renderPage()
    await screen.findByText('メンテナンスのお知らせ')
    fill('新機能のお知らせ', '検索ができるようになりました。')

    fireEvent.click(screen.getByRole('button', { name: '発行する' }))

    // まだ送らない。ブラウザの確認ダイアログも使わない
    expect(posts()).toHaveLength(0)
    expect(window.confirm).not.toHaveBeenCalled()
    const dialog = screen.getByRole('alertdialog')
    expect(dialog.textContent).toContain('新機能のお知らせ')
    expect(dialog.textContent).toContain('検索ができるようになりました。')
    expect(dialog.textContent).toContain('訂正・取り消しはできません')
    expect(dialog.textContent).toContain('利用者側の表示ができたあとは')
  })

  it('確認で発行すると、その場で一覧の先頭に出て、入力欄は空に戻る', async () => {
    renderPage()
    await screen.findByText('メンテナンスのお知らせ')
    fill('新機能のお知らせ', '1行目\n2行目')

    fireEvent.click(screen.getByRole('button', { name: '発行する' }))
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '発行する' }))

    expect(await screen.findByText('お知らせを発行しました')).toBeTruthy()
    expect(within(items()[0]).getByRole('heading', { name: '新機能のお知らせ' })).toBeTruthy()
    expect(items()[0].querySelector('.admin-announcements-body')?.textContent).toBe('1行目\n2行目')
    expect(items()[0].textContent).toContain('2026/10/09 15:30')
    expect(items()).toHaveLength(3)
    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect((screen.getByRole('textbox', { name: '題名' }) as HTMLInputElement).value).toBe('')
    expect((screen.getByRole('textbox', { name: '本文' }) as HTMLTextAreaElement).value).toBe('')
    expect(posts()).toEqual([
      { method: 'POST', path: '/api/admin/announcements', query: '', body: { title: '新機能のお知らせ', body: '1行目\n2行目' } },
    ])
  })

  it('確認で「戻って直す」を押すと、送らずに入力をそのまま残す', async () => {
    renderPage()
    await screen.findByText('メンテナンスのお知らせ')
    fill('新機能のお知らせ', '本文')

    fireEvent.click(screen.getByRole('button', { name: '発行する' }))
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '戻って直す' }))

    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(posts()).toHaveLength(0)
    expect((screen.getByRole('textbox', { name: '題名' }) as HTMLInputElement).value).toBe('新機能のお知らせ')
  })

  it('送信中は発行ボタンを押せず、続けて押しても1回しか送らない', async () => {
    renderPage()
    await screen.findByText('メンテナンスのお知らせ')
    fill('新機能のお知らせ', '本文')
    fireEvent.click(screen.getByRole('button', { name: '発行する' }))
    let release!: () => void
    holdNext = new Promise((resolve) => (release = resolve))

    const ok = within(screen.getByRole('alertdialog')).getByRole('button', { name: '発行する' }) as HTMLButtonElement
    fireEvent.click(ok)
    expect(ok.disabled).toBe(true)
    expect(ok.textContent).toBe('発行中…')
    fireEvent.click(ok)
    fireEvent.click(ok)
    expect(posts()).toHaveLength(1)

    await act(async () => release())
    expect(await screen.findByText('お知らせを発行しました')).toBeTruthy()
  })

  it('題名か本文が空のときは、発行ボタンを押せない', async () => {
    renderPage()
    await screen.findByText('メンテナンスのお知らせ')
    const button = screen.getByRole('button', { name: '発行する' }) as HTMLButtonElement

    expect(button.disabled).toBe(true)
    fill('題名だけ', '  \n ')
    expect(button.disabled).toBe(true)
    fill('', '本文だけ')
    expect(button.disabled).toBe(true)
    fill('題名', '本文')
    expect(button.disabled).toBe(false)
  })

  it('文字数の上限を超えると、確認を出さずに、どの欄が何文字までかを画面に出す', async () => {
    renderPage()
    await screen.findByText('メンテナンスのお知らせ')
    fill('あ'.repeat(101), 'い'.repeat(2001))

    expect(screen.getByText('101 / 100')).toBeTruthy()
    expect(screen.getByText('2001 / 2000')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '発行する' }))

    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(screen.getByText('題名は100文字以内で入力してください')).toBeTruthy()
    expect(screen.getByText('本文は2000文字以内で入力してください')).toBeTruthy()
    expect(screen.getByRole('textbox', { name: '題名' }).getAttribute('aria-invalid')).toBe('true')
    expect(posts()).toHaveLength(0)
  })

  it('上限ちょうどなら確認に進める', async () => {
    renderPage()
    await screen.findByText('メンテナンスのお知らせ')
    fill('あ'.repeat(100), 'い'.repeat(2000))

    fireEvent.click(screen.getByRole('button', { name: '発行する' }))

    expect(screen.getByRole('alertdialog')).toBeTruthy()
  })

  it('サーバーで弾かれたときは、サーバーの理由を欄の下に出し、入力を残す', async () => {
    renderPage()
    await screen.findByText('メンテナンスのお知らせ')
    fill('題名', '本文')
    rejectNext = { body: '本文は2000文字以内で入力してください' }

    fireEvent.click(screen.getByRole('button', { name: '発行する' }))
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '発行する' }))

    expect(await screen.findByText('本文は2000文字以内で入力してください')).toBeTruthy()
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
    expect(items()).toHaveLength(2)
    expect((screen.getByRole('textbox', { name: '本文' }) as HTMLTextAreaElement).value).toBe('本文')
  })
})
