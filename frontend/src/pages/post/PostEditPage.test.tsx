import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { MastersResponse } from '../../api/masters'
import type { PostResponse, UpdatePostRequest } from '../../api/posts'
import PostEditPage from './PostEditPage'

/*
 * 投稿の編集（docs/post.md「投稿の編集」）の確認。
 * 写真は変えられず、文字の情報だけを編集する。入力 → 確認 → 保存 → 詳細画面、の流れは投稿の作成と同じ
 */

const fetchPost = vi.fn<(id: number) => Promise<PostResponse>>()
const updatePost = vi.fn<(id: number, request: UpdatePostRequest) => Promise<PostResponse>>()
vi.mock('../../api/posts', () => ({
  fetchPost: (id: number) => fetchPost(id),
  updatePost: (id: number, request: UpdatePostRequest) => updatePost(id, request),
}))
const fetchMasters = vi.fn<() => Promise<MastersResponse>>()
vi.mock('../../api/masters', () => ({ fetchMasters: () => fetchMasters() }))
vi.mock('../../api/tags', () => ({ searchTags: async () => [] }))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token' }) }))

const MASTERS: MastersResponse = {
  fashionCategories: [
    { id: 1, name: 'きれいめ' },
    { id: 2, name: 'カジュアル' },
  ],
  bodyTypes: [],
  personalColors: [],
  tags: [
    { id: 10, name: '古着' },
    { id: 11, name: '冬コーデ' },
  ],
  ageGroups: [],
  genders: [],
}

function post(overrides: Partial<PostResponse> = {}): PostResponse {
  return {
    id: 1,
    title: '秋の羽織りもの',
    fashionCategory: { id: 1, name: 'きれいめ' },
    tags: [
      { id: 10, name: '古着', official: true },
      { id: 50, name: '手入力のタグ', official: false },
    ],
    wornItems: 'コート：古着屋',
    description: '丈が長めのコートです',
    referenceUrl: null,
    imageUrls: ['http://localhost/uploads/a.jpg', 'http://localhost/uploads/b.jpg'],
    author: { id: 2, username: 'haru_st', profileImageUrl: null, heightCm: null },
    likeCount: 3,
    likedByMe: false,
    favoritedByMe: false,
    ownedByMe: true,
    createdAt: '2026-10-08T12:00:00',
    editedAt: null,
    ...overrides,
  }
}

beforeEach(() => {
  fetchMasters.mockResolvedValue(MASTERS)
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
})

afterEach(() => {
  cleanup()
  fetchPost.mockReset()
  updatePost.mockReset()
  fetchMasters.mockReset()
  vi.restoreAllMocks()
})

function renderEdit(response: PostResponse | Error) {
  if (response instanceof Error) fetchPost.mockRejectedValue(response)
  else fetchPost.mockResolvedValue(response)
  return render(
    <MemoryRouter initialEntries={['/posts/1', '/posts/1/edit']} initialIndex={1}>
      <Routes>
        <Route path="/posts/:postId" element={<p>詳細画面</p>} />
        <Route path="/posts/:postId/edit" element={<PostEditPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

function titleInput(): HTMLInputElement {
  return screen.getByLabelText(/題名/) as HTMLInputElement
}

/** 選択肢を読み込み終わり、入力欄が出るまで待つ（読み込み中も見出しは出ているため、見出しでは待たない） */
async function formReady() {
  await screen.findByRole('button', { name: '確認' })
}

async function goToConfirm() {
  fireEvent.click(screen.getByRole('button', { name: '確認' }))
  await screen.findByRole('heading', { name: '編集内容の確認' })
}

describe('編集画面を開く', () => {
  it('自分の投稿なら、今の内容が入った編集画面を出す', async () => {
    renderEdit(post())
    await formReady()

    expect(screen.getByRole('heading', { name: '投稿の編集' })).toBeTruthy()
    expect(titleInput().value).toBe('秋の羽織りもの')
    expect(screen.getByText('きれいめ')).toBeTruthy()
    expect(screen.getByText('古着・手入力のタグ')).toBeTruthy()
    expect((screen.getByLabelText(/投稿説明/) as HTMLTextAreaElement).value).toBe('丈が長めのコートです')
  })

  it('写真は表示するが変えられず、変えたいときは削除して投稿し直すことを書く', async () => {
    const { container } = renderEdit(post())
    await formReady()

    expect(screen.queryByText('写真を選ぶ')).toBeNull()
    expect(container.querySelector('input[type="file"]')).toBeNull()
    expect(screen.queryByRole('button', { name: /枚目の写真を削除/ })).toBeNull()
    expect(screen.getByText('1 / 2')).toBeTruthy()
    const note = container.querySelector('.post-edit-photo-note')?.textContent ?? ''
    expect(note).toContain('写真は変更できません')
    expect(note).toContain('削除して投稿し直して')
    expect(note).toContain('いいね')
  })

  it('他人の投稿なら、編集画面を出さずに「この投稿は編集できません」と出す', async () => {
    renderEdit(post({ ownedByMe: false }))

    expect(await screen.findByText('この投稿は編集できません')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '確認' })).toBeNull()
  })

  it('投稿が見つからなければ「投稿が見つかりません」と出す', async () => {
    renderEdit(new ApiError(404, { message: '投稿が見つかりません。', errors: {} }, '投稿が見つかりません。'))

    expect(await screen.findByText('投稿が見つかりません')).toBeTruthy()
    expect(screen.queryByRole('button', { name: '確認' })).toBeNull()
  })
})

describe('保存', () => {
  it('確認画面を通って「保存する」で文字の情報だけを送り、詳細画面へ移る', async () => {
    updatePost.mockResolvedValue(post({ title: '冬のコート' }))
    renderEdit(post())
    await formReady()

    fireEvent.change(titleInput(), { target: { value: '  冬のコート ' } })
    await goToConfirm()
    expect(screen.getByText('冬のコート')).toBeTruthy()
    expect(updatePost).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('詳細画面')).toBeTruthy()
    expect(updatePost).toHaveBeenCalledWith(1, {
      title: '冬のコート',
      fashionCategoryId: 1,
      tags: ['古着', '手入力のタグ'],
      wornItems: 'コート：古着屋',
      description: '丈が長めのコートです',
      referenceUrl: '',
    })
  })

  it('入力に誤りがあれば、確認画面へ進まない', async () => {
    renderEdit(post())
    await formReady()

    fireEvent.change(titleInput(), { target: { value: ' ' } })
    fireEvent.click(screen.getByRole('button', { name: '確認' }))

    expect(screen.queryByRole('heading', { name: '編集内容の確認' })).toBeNull()
    expect(screen.getByText('題名を入力してください')).toBeTruthy()
  })

  it('サーバーで入力の誤り（400）と言われたら、入力画面に戻して該当する欄に出す', async () => {
    updatePost.mockRejectedValue(
      new ApiError(400, { message: '入力内容に誤りがあります。', errors: { tags: '「古着」は使用できないタグです' } }, '入力内容に誤りがあります。'),
    )
    renderEdit(post())
    await formReady()
    await goToConfirm()

    fireEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('「古着」は使用できないタグです')).toBeTruthy()
    expect(screen.getByRole('heading', { name: '投稿の編集' })).toBeTruthy()
  })

  it('他人の投稿（403）と言われたら、確認画面のまま理由を出す', async () => {
    updatePost.mockRejectedValue(new ApiError(403, { message: 'この投稿は編集できません。', errors: {} }, 'この投稿は編集できません。'))
    renderEdit(post())
    await formReady()
    await goToConfirm()

    fireEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('この投稿は編集できません。')).toBeTruthy()
    expect(screen.getByRole('heading', { name: '編集内容の確認' })).toBeTruthy()
  })
})

describe('無効にされたファッションの種類', () => {
  it('設定済みの種類が選択肢にない（無効）なら、その名前を出し、パネルでも「現在は選べません」と添えて選べる', async () => {
    updatePost.mockResolvedValue(post())
    renderEdit(post({ fashionCategory: { id: 9, name: 'ストリート' } }))
    await formReady()

    expect(screen.getByText('ストリート')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: /ファッション選択/ }))
    const group = screen.getByRole('radiogroup', { name: 'ファッションの種類' })
    const current = within(group).getByRole('radio', { name: /ストリート/ })
    expect(current.getAttribute('aria-checked')).toBe('true')
    expect(current.textContent).toContain('現在は選べません')
    expect(within(group).getByRole('radio', { name: /カジュアル/ })).toBeTruthy()
    fireEvent.click(current)

    await goToConfirm()
    fireEvent.click(screen.getByRole('button', { name: '保存する' }))
    await screen.findByText('詳細画面')
    expect(updatePost.mock.calls[0][1].fashionCategoryId).toBe(9)
  })
})
