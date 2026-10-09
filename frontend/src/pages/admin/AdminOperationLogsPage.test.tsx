import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminOperationLogsPage from './AdminOperationLogsPage'

/** 操作ログの画面（docs/admin.md「管理操作のログ」）の確認 */

vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true }) }))

type Log = {
  id: number
  operatedAt: string
  operatorId: number | null
  operatorUsername: string
  system: boolean
  action: string
  targetType: string
  targetId: number
  detail: string | null
}

let stored: Log[]
let queries: string[]

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function log(id: number, action: string, targetType: string, overrides: Partial<Log> = {}): Log {
  return {
    id,
    operatedAt: `2026-10-09T10:${String(id % 60).padStart(2, '0')}:00.5`,
    operatorId: 1,
    operatorUsername: 'admin',
    system: false,
    action,
    targetType,
    targetId: id * 10,
    detail: `名前：項目${id}`,
    ...overrides,
  }
}

beforeEach(() => {
  stored = [
    log(6, 'ANNOUNCEMENT_PUBLISHED', 'ANNOUNCEMENT', { detail: '題名：メンテナンスのお知らせ' }),
    log(5, 'TAG_MADE_OFFICIAL', 'TAG'),
    log(4, 'MASTER_ACTIVATED', 'PERSONAL_COLOR'),
    log(3, 'MASTER_DEACTIVATED', 'BODY_TYPE'),
    log(2, 'MASTER_CREATED', 'FASHION_CATEGORY'),
    log(1, 'USER_PROMOTED_TO_ADMIN', 'USER', {
      operatorId: null,
      operatorUsername: 'システム',
      system: true,
      detail: 'ADMIN_EMAIL による起動時の昇格（username=admin）',
    }),
  ]
  queries = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const url = new URL(String(input))
      if (url.pathname !== '/api/admin/operation-logs') return json(404, null)
      queries.push(url.search)
      const page = Number(url.searchParams.get('page'))
      const size = Number(url.searchParams.get('size'))
      return json(200, {
        logs: stored.slice(page * size, page * size + size),
        page,
        size,
        hasNext: stored.length > (page + 1) * size,
      })
    }),
  )
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminOperationLogsPage />
    </MemoryRouter>,
  )
}

/** 見出しの行を除いた行 */
function bodyRows(): HTMLElement[] {
  return screen.getAllByRole('row').slice(1)
}

function cells(row: HTMLElement): string[] {
  return within(row).getAllByRole('cell').map((cell) => cell.textContent ?? '')
}

describe('操作ログの一覧', () => {
  it('新しい順に、日時・操作した人・操作の種類・対象・内容を出す', async () => {
    renderPage()
    await screen.findByText('題名：メンテナンスのお知らせ')

    const headers = screen.getAllByRole('columnheader').map((h) => h.textContent)
    expect(headers).toEqual(['日時', '操作した人', '操作の種類', '対象', '内容'])
    expect(cells(bodyRows()[0])).toEqual([
      '2026/10/09 10:06:00',
      'admin',
      'お知らせの発行',
      'お知らせ（ID: 60）',
      '題名：メンテナンスのお知らせ',
    ])
    expect(queries[0]).toBe('?page=0&size=20')
  })

  it('操作の種類・対象の種類は、英語の定数名ではなく日本語で出す', async () => {
    const { container } = renderPage()
    await screen.findByText('題名：メンテナンスのお知らせ')

    expect(bodyRows().map((row) => cells(row)[2])).toEqual([
      'お知らせの発行',
      '手入力のタグを公式タグにした',
      '有効に戻した',
      '無効にした',
      '追加した',
      '管理者にした',
    ])
    expect(bodyRows().map((row) => cells(row)[3])).toEqual([
      'お知らせ（ID: 60）',
      'タグ（ID: 50）',
      'パーソナルカラー（ID: 40）',
      '骨格タイプ（ID: 30）',
      'ファッションの種類（ID: 20）',
      'ユーザー（ID: 10）',
    ])
    for (const constant of ['ANNOUNCEMENT', 'MASTER_', 'TAG_MADE', 'PERSONAL_COLOR', 'BODY_TYPE', 'USER_PROMOTED']) {
      expect(container.querySelector('table')?.textContent).not.toContain(constant)
    }
  })

  it('システムの操作は、システムが行ったと分かるように出す', async () => {
    renderPage()
    await screen.findByText('題名：メンテナンスのお知らせ')

    const systemRow = bodyRows()[5]
    expect(systemRow.classList.contains('admin-logs-row-system')).toBe(true)
    const operator = within(systemRow).getAllByRole('cell')[1]
    expect(operator.querySelector('.admin-logs-system')?.textContent).toBe('システム')
    expect(operator.textContent).toContain('自動')
    // 人の操作には付けない
    expect(bodyRows()[0].classList.contains('admin-logs-row-system')).toBe(false)
    expect(bodyRows()[0].querySelector('.admin-logs-system')).toBeNull()
  })

  it('後から増えた操作の種類など、知らない値は定数名のまま出す（表示が消えないように）', async () => {
    stored = [log(1, 'USER_SUSPENDED', 'REPORT', { detail: null })]
    renderPage()

    await screen.findByText('USER_SUSPENDED')
    expect(cells(bodyRows()[0])).toEqual(['2026/10/09 10:01:00', 'admin', 'USER_SUSPENDED', 'REPORT（ID: 10）', ''])
  })

  it('ログがなければ、そのことを出す', async () => {
    stored = []
    renderPage()

    expect(await screen.findByText('操作ログはまだありません。')).toBeTruthy()
  })

  it('読み込めなかったときは、エラーを画面に出す', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(500, null)))
    renderPage()

    expect(await screen.findByRole('alert')).toBeTruthy()
  })
})

describe('ページ送り', () => {
  beforeEach(() => {
    stored = Array.from({ length: 45 }, (_, i) => log(45 - i, 'MASTER_CREATED', 'BODY_TYPE'))
  })

  it('20件ずつ区切り、「次へ」「前へ」でページを移る', async () => {
    renderPage()
    await screen.findByText('名前：項目45')
    expect(bodyRows()).toHaveLength(20)
    expect(screen.getByText('1ページ目')).toBeTruthy()
    const prev = screen.getByRole('button', { name: '前へ' }) as HTMLButtonElement
    const next = screen.getByRole('button', { name: '次へ' }) as HTMLButtonElement
    expect(prev.disabled).toBe(true)

    fireEvent.click(next)
    await screen.findByText('名前：項目25')
    expect(screen.queryByText('名前：項目45')).toBeNull()
    expect(screen.getByText('2ページ目')).toBeTruthy()
    expect(prev.disabled).toBe(false)

    fireEvent.click(next)
    await screen.findByText('名前：項目5')
    expect(bodyRows()).toHaveLength(5)
    expect(next.disabled).toBe(true)

    fireEvent.click(prev)
    await screen.findByText('名前：項目25')
    expect(queries).toEqual(['?page=0&size=20', '?page=1&size=20', '?page=2&size=20', '?page=1&size=20'])
  })

  it('1ページに収まるときは、どちらのボタンも押せない', async () => {
    stored = stored.slice(0, 3)
    renderPage()
    await screen.findByText('名前：項目45')

    expect((screen.getByRole('button', { name: '前へ' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('button', { name: '次へ' }) as HTMLButtonElement).disabled).toBe(true)
  })
})
