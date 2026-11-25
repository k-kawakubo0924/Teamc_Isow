import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AdminMastersPage from './AdminMastersPage'

/*
 * マスタ管理の画面（docs/admin.md「マスタの管理」）の確認。
 * fetch を、管理 API と同じ形で答える小さな偽のサーバーに置き換え、URL の組み立てから画面の更新までを確かめる。
 */

vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true }) }))

type Item = { id: number; name: string; displayOrder: number; active: boolean }

/** 4種類（タブの名前・API のパス） */
const KINDS = [
  { label: 'ファッションの種類', path: '/api/admin/masters/fashion-categories', firstName: 'きれいめ' },
  { label: '骨格タイプ', path: '/api/admin/masters/body-types', firstName: 'ストレート' },
  { label: 'パーソナルカラー', path: '/api/admin/masters/personal-colors', firstName: 'イエベ春' },
  { label: '公式タグ', path: '/api/admin/official-tags', firstName: '古着' },
] as const

let server: Record<string, Item[]>
/** 公式タグを追加したときに、公式にする手入力のタグ（名前 → ID） */
let userTags: Record<string, number>
let requests: { method: string; path: string; body: unknown }[]
/** 次の応答を止めておく（送信中の表示の確認用） */
let holdNext: Promise<void> | null

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function badRequest(field: string, message: string) {
  return json(400, { message: '入力内容に誤りがあります。赤い欄を修正してください。', errors: { [field]: message } })
}

async function fakeFetch(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
  const path = new URL(String(input)).pathname
  const method = init?.method ?? 'GET'
  const body = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined
  requests.push({ method, path, body })
  if (holdNext) {
    const hold = holdNext
    holdNext = null
    await hold
  }

  const action = path.match(/^(.*)\/(\d+)\/(deactivate|activate)$/)
  if (action && method === 'POST') {
    const item = server[action[1]]?.find((i) => i.id === Number(action[2]))
    if (!item) return json(404, null)
    item.active = action[3] === 'activate'
    return json(200, { ...item })
  }
  const items = server[path]
  if (!items) return json(404, null)
  if (method === 'GET') return json(200, { items: items.map((i) => ({ ...i })) })

  const name = String(body.name).trim()
  const limit = path.endsWith('official-tags') ? 30 : 50
  if (name.length > limit) return badRequest('name', `名前は${limit}文字以内で入力してください`)
  if (items.some((i) => i.name.toLowerCase() === name.toLowerCase())) {
    return badRequest('name', '同じ名前がすでにあります（無効にしているものも含みます）')
  }
  const created = {
    id: userTags[name] ?? 100 + requests.length,
    name,
    displayOrder: Math.max(...items.map((i) => i.displayOrder)) + 10,
    active: true,
  }
  items.push(created)
  if (path.endsWith('official-tags')) {
    const madeOfficial = name in userTags
    return json(madeOfficial ? 200 : 201, { tag: { ...created }, madeOfficial })
  }
  return json(201, { ...created })
}

beforeEach(() => {
  server = {}
  KINDS.forEach((kind, k) => {
    server[kind.path] = [
      { id: k * 10 + 1, name: kind.firstName, displayOrder: 10, active: true },
      { id: k * 10 + 2, name: `${kind.label}の無効なもの`, displayOrder: 20, active: false },
    ]
  })
  userTags = { Y2K: 77 }
  requests = []
  holdNext = null
  vi.stubGlobal('fetch', vi.fn(fakeFetch))
  vi.stubGlobal('confirm', vi.fn(() => true))
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/admin/masters']}>
      <AdminMastersPage />
    </MemoryRouter>,
  )
}

/** 種類を選び、一覧が出るまで待つ */
async function openKind(label: string, firstName: string) {
  renderPage()
  fireEvent.click(screen.getByRole('tab', { name: label }))
  await screen.findByText(firstName)
}

function row(name: string): HTMLElement {
  return screen.getByText(name).closest('tr') as HTMLElement
}

function addName(name: string) {
  fireEvent.change(screen.getByRole('textbox', { name: '追加する名前' }), { target: { value: name } })
  fireEvent.click(screen.getByRole('button', { name: '追加' }))
}

describe.each(KINDS)('$label', ({ label, path, firstName }) => {
  it('一覧：無効なものも出し、無効だと見て分かる', async () => {
    await openKind(label, firstName)

    expect(screen.getByRole('tab', { name: label }).getAttribute('aria-selected')).toBe('true')
    const active = row(firstName)
    expect(within(active).getByText('有効')).toBeTruthy()
    expect(within(active).getByRole('button', { name: '無効にする' })).toBeTruthy()
    expect(active.classList.contains('admin-masters-row-inactive')).toBe(false)

    const inactive = row(`${label}の無効なもの`)
    expect(within(inactive).getByText('無効')).toBeTruthy()
    expect(within(inactive).getByRole('button', { name: '有効に戻す' })).toBeTruthy()
    expect(inactive.classList.contains('admin-masters-row-inactive')).toBe(true)
    expect(requests).toContainEqual({ method: 'GET', path, body: undefined })
  })

  it('追加：名前を入れて追加すると、一覧の末尾にその場で出る', async () => {
    await openKind(label, firstName)

    addName('新しい名前')

    expect(await screen.findByText('「新しい名前」を追加しました')).toBeTruthy()
    const rows = screen.getAllByRole('row')
    expect(within(rows[rows.length - 1]).getByText('新しい名前')).toBeTruthy()
    expect(within(rows[rows.length - 1]).getByText('有効')).toBeTruthy()
    expect((screen.getByRole('textbox', { name: '追加する名前' }) as HTMLInputElement).value).toBe('')
    expect(requests).toContainEqual({ method: 'POST', path, body: { name: '新しい名前' } })
  })

  it('無効化：押してもすぐには実行せず、画面の中で確かめてから無効にする', async () => {
    await openKind(label, firstName)

    fireEvent.click(within(row(firstName)).getByRole('button', { name: '無効にする' }))

    // まだ送らない。ブラウザの確認ダイアログも使わない
    expect(requests.filter((r) => r.method === 'POST')).toHaveLength(0)
    expect(window.confirm).not.toHaveBeenCalled()
    const dialog = screen.getByRole('alertdialog')
    expect(dialog.textContent).toContain(`「${firstName}」を無効にしますか？`)
    expect(dialog.textContent).toContain('既存の投稿・プロフィールの表示はそのまま')

    fireEvent.click(within(dialog).getByRole('button', { name: '無効にする' }))

    await waitFor(() => expect(within(row(firstName)).getByText('無効')).toBeTruthy())
    expect(row(firstName).classList.contains('admin-masters-row-inactive')).toBe(true)
    expect(within(row(firstName)).getByRole('button', { name: '有効に戻す' })).toBeTruthy()
    expect(screen.queryByRole('alertdialog')).toBeNull()
    const id = server[path][0].id
    expect(requests).toContainEqual({ method: 'POST', path: `${path}/${id}/deactivate`, body: undefined })
  })

  it('無効化の確認で「やめる」を押すと、何もしない', async () => {
    await openKind(label, firstName)

    fireEvent.click(within(row(firstName)).getByRole('button', { name: '無効にする' }))
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'やめる' }))

    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(within(row(firstName)).getByText('有効')).toBeTruthy()
    expect(requests.filter((r) => r.method === 'POST')).toHaveLength(0)
  })

  it('有効に戻す：一覧の行から戻すと、その場で有効になる', async () => {
    await openKind(label, firstName)
    const name = `${label}の無効なもの`

    fireEvent.click(within(row(name)).getByRole('button', { name: '有効に戻す' }))

    await waitFor(() => expect(within(row(name)).getByText('有効')).toBeTruthy())
    expect(row(name).classList.contains('admin-masters-row-inactive')).toBe(false)
    const id = server[path][1].id
    expect(requests).toContainEqual({ method: 'POST', path: `${path}/${id}/activate`, body: undefined })
  })

  it('重複した名前を追加すると、何が悪いのかが画面に出て、一覧は変わらない', async () => {
    await openKind(label, firstName)
    const before = screen.getAllByRole('row').length

    addName(firstName)

    const error = await screen.findByText('同じ名前がすでにあります（無効にしているものも含みます）')
    expect(error.getAttribute('role')).toBe('alert')
    expect(screen.getByRole('textbox', { name: '追加する名前' }).getAttribute('aria-invalid')).toBe('true')
    expect(screen.getAllByRole('row')).toHaveLength(before)
    // 入力した名前は消さない（直して送り直せるように）
    expect((screen.getByRole('textbox', { name: '追加する名前' }) as HTMLInputElement).value).toBe(firstName)
  })
})

describe('追加のエラー・送信中', () => {
  it('文字数の上限を超えると、上限が分かる文言が画面に出る', async () => {
    await openKind('骨格タイプ', 'ストレート')

    addName('あ'.repeat(51))

    expect(await screen.findByText('名前は50文字以内で入力してください')).toBeTruthy()
  })

  it('公式タグは30文字が上限で、その文言が出る', async () => {
    await openKind('公式タグ', '古着')

    addName('あ'.repeat(31))

    expect(await screen.findByText('名前は30文字以内で入力してください')).toBeTruthy()
  })

  it('送信中は追加ボタンを押せず、続けて押しても1回しか送らない', async () => {
    await openKind('パーソナルカラー', 'イエベ春')
    let release!: () => void
    holdNext = new Promise((resolve) => (release = resolve))

    addName('ブルベ冬')
    const button = screen.getByRole('button', { name: '追加' }) as HTMLButtonElement
    expect(button.disabled).toBe(true)
    fireEvent.click(button)
    fireEvent.click(button)
    expect(requests.filter((r) => r.method === 'POST')).toHaveLength(1)

    await act(async () => release())
    expect(await screen.findByText('「ブルベ冬」を追加しました')).toBeTruthy()
    // 送信が終われば、次の名前を入れて押せる
    fireEvent.change(screen.getByRole('textbox', { name: '追加する名前' }), { target: { value: 'ブルベ夏' } })
    expect(button.disabled).toBe(false)
  })

  it('名前が空のときは追加ボタンを押せない', async () => {
    await openKind('骨格タイプ', 'ストレート')

    fireEvent.change(screen.getByRole('textbox', { name: '追加する名前' }), { target: { value: '   ' } })

    expect((screen.getByRole('button', { name: '追加' }) as HTMLButtonElement).disabled).toBe(true)
  })
})

describe('公式タグ', () => {
  it('同じ名前の手入力のタグがあれば「既存のタグを公式にしました」と出し、一覧に加える', async () => {
    await openKind('公式タグ', '古着')

    addName('Y2K')

    expect(await screen.findByText('既存のタグを公式にしました（「Y2K」）')).toBeTruthy()
    expect(within(row('Y2K')).getByText('有効')).toBeTruthy()
    expect(screen.queryByText('「Y2K」を追加しました')).toBeNull()
  })

  it('新しい名前なら、通常どおり「追加しました」と出す', async () => {
    await openKind('公式タグ', '古着')

    addName('韓国')

    expect(await screen.findByText('「韓国」を追加しました')).toBeTruthy()
    expect(screen.queryByText(/既存のタグを公式にしました/)).toBeNull()
  })
})

describe('種類の切り替え', () => {
  it('最初はファッションの種類を出し、切り替えるとその種類の一覧に変わる（前の種類のメッセージは消す）', async () => {
    renderPage()
    expect(await screen.findByText('きれいめ')).toBeTruthy()
    expect(screen.getByRole('tab', { name: 'ファッションの種類' }).getAttribute('aria-selected')).toBe('true')
    addName('モード')
    await screen.findByText('「モード」を追加しました')

    fireEvent.click(screen.getByRole('tab', { name: '骨格タイプ' }))

    expect(await screen.findByText('ストレート')).toBeTruthy()
    expect(screen.queryByText('きれいめ')).toBeNull()
    expect(screen.queryByText('「モード」を追加しました')).toBeNull()
  })

  it('一覧を読み込めなかったときは、エラーを画面に出す', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(500, null)))
    renderPage()

    expect(await screen.findByRole('alert')).toBeTruthy()
  })

  it('無効化に失敗したときは、エラーを画面に出し、一覧は変えない', async () => {
    await openKind('骨格タイプ', 'ストレート')
    server['/api/admin/masters/body-types'] = []

    fireEvent.click(within(row('ストレート')).getByRole('button', { name: '無効にする' }))
    fireEvent.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: '無効にする' }))

    expect(await screen.findByRole('alert')).toBeTruthy()
    expect(within(row('ストレート')).getByText('有効')).toBeTruthy()
  })
})
