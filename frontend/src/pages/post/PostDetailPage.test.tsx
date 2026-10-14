import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { PostResponse } from '../../api/posts'
import PostDetailPage from './PostDetailPage'

// API とログイン状態は差し替える（画面の表示だけを確かめる）
const fetchPost = vi.fn<(id: number) => Promise<PostResponse>>()
const deletePost = vi.fn<(id: number) => Promise<void>>()
vi.mock('../../api/posts', () => ({
  fetchPost: (id: number) => fetchPost(id),
  deletePost: (id: number) => deletePost(id),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token' }) }))
const blockUser = vi.fn<(id: number) => Promise<void>>()
vi.mock('../../api/blocks', () => ({ blockUser: (id: number) => blockUser(id) }))
vi.mock('../../api/reports', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/reports')>()),
  reportTarget: vi.fn(),
}))

afterEach(() => {
  cleanup()
  fetchPost.mockReset()
  deletePost.mockReset()
})

function post(overrides: Partial<PostResponse> = {}, author: Partial<PostResponse['author']> = {}): PostResponse {
  return {
    id: 1,
    title: '秋の羽織りもの',
    fashionCategory: { id: 1, name: 'きれいめ' },
    tags: [{ id: 1, name: '古着', official: true }],
    wornItems: null,
    description: '説明',
    referenceUrl: null,
    imageUrls: ['http://localhost/uploads/a.jpg'],
    author: { id: 2, username: 'haru_st', profileImageUrl: null, heightCm: null, ...author },
    likeCount: 0,
    likedByMe: false,
    favoritedByMe: false,
    ownedByMe: false,
    createdAt: '2026-10-08T12:00:00',
    editedAt: null,
    ...overrides,
  }
}

async function open(response: PostResponse) {
  fetchPost.mockResolvedValue(response)
  const result = render(
    <MemoryRouter initialEntries={['/posts/1']}>
      <Routes>
        <Route path="/posts/:postId" element={<PostDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
  await screen.findByText('@haru_st')
  return result
}

describe('投稿の詳細画面', () => {
  it('身長が未設定（null）の投稿者なら、身長の部分そのものを出さない', async () => {
    const { container } = await open(post())

    expect(container.querySelector('.post-detail-height')).toBeNull()
    expect(container.textContent).not.toMatch(/nullcm|undefinedcm/)
  })

  it('身長の項目がない応答（古いサーバーなど）でも「undefinedcm」と出さない', async () => {
    const response = post()
    // 項目そのものが返ってこない場合を再現する
    delete (response.author as Partial<PostResponse['author']>).heightCm
    const { container } = await open(response)

    expect(container.querySelector('.post-detail-height')).toBeNull()
    expect(container.textContent).not.toContain('undefined')
  })

  it('身長が設定されていれば「160cm」と出す', async () => {
    const { container } = await open(post({}, { heightCm: 160 }))

    expect(container.querySelector('.post-detail-height')?.textContent).toBe('160cm')
  })

  it('写真が1枚の投稿では「1 / 1」の枚数表示と ‹ › のボタンを出さない', async () => {
    const { container } = await open(post())

    expect(container.querySelector('.post-photo-count')).toBeNull()
    expect(screen.queryByRole('button', { name: '次の写真' })).toBeNull()
  })

  it('写真が2枚の投稿では枚数表示を出す', async () => {
    await open(post({ imageUrls: ['http://localhost/uploads/a.jpg', 'http://localhost/uploads/b.jpg'] }))

    expect(screen.getByText('1 / 2')).toBeTruthy()
  })

  it('参考情報は http・https のときだけリンクにし、別タブで安全に開く', async () => {
    await open(post({ referenceUrl: 'https://example.com/item' }))

    const link = screen.getByRole('link', { name: 'https://example.com/item' })
    expect(link.getAttribute('target')).toBe('_blank')
    expect(link.getAttribute('rel')).toBe('noopener noreferrer nofollow')
  })

  it('http・https 以外の参考情報はリンクにしない', async () => {
    await open(post({ referenceUrl: 'javascript:alert(1)' }))

    expect(screen.queryByRole('link', { name: 'javascript:alert(1)' })).toBeNull()
    expect(screen.getByText('javascript:alert(1)')).toBeTruthy()
  })
})

/** 削除の後に移る先（プロフィール）。「戻る」で、削除した投稿の詳細に戻らないことを確かめるためのボタンを置く */
function ProfileStub() {
  const navigate = useNavigate()
  return (
    <div>
      <p>プロフィール画面</p>
      <button type="button" onClick={() => navigate(-1)}>
        ブラウザの戻る
      </button>
    </div>
  )
}

/** ホーム → 投稿の詳細、の順に開いた状態にする */
async function openFromHome(response: PostResponse) {
  fetchPost.mockResolvedValue(response)
  render(
    <MemoryRouter initialEntries={['/', '/posts/1']} initialIndex={1}>
      <Routes>
        <Route path="/" element={<p>ホーム画面</p>} />
        <Route path="/posts/:postId" element={<PostDetailPage />} />
        <Route path="/profile" element={<ProfileStub />} />
      </Routes>
    </MemoryRouter>,
  )
  await screen.findByText('@haru_st')
}

function openDeleteConfirm() {
  fireEvent.click(screen.getByRole('button', { name: '投稿のメニュー' }))
  fireEvent.click(screen.getByRole('menuitem', { name: '削除' }))
}

describe('投稿の削除', () => {
  it('他人の投稿のメニューには、削除（と編集）を出さない', async () => {
    await openFromHome(post({ ownedByMe: false }))

    fireEvent.click(screen.getByRole('button', { name: '投稿のメニュー' }))

    expect(screen.queryByRole('menuitem', { name: '削除' })).toBeNull()
    expect(screen.queryByRole('menuitem', { name: '編集' })).toBeNull()
  })

  it('自分の投稿では、メニューの「削除」で画面の中に確認を出し、まだ削除しない', async () => {
    await openFromHome(post({ ownedByMe: true }))

    openDeleteConfirm()

    const dialog = screen.getByRole('alertdialog')
    expect(dialog.textContent).toContain('この投稿を削除しますか？')
    expect(dialog.textContent).toContain('元に戻せません')
    expect(deletePost).not.toHaveBeenCalled()
  })

  it('「キャンセル」で確認を閉じ、削除しない', async () => {
    await openFromHome(post({ ownedByMe: true }))
    openDeleteConfirm()

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }))

    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(deletePost).not.toHaveBeenCalled()
    expect(screen.getByText('@haru_st')).toBeTruthy()
  })

  it('「削除する」で削除し、プロフィールへ移る。戻るで削除した投稿には戻らない', async () => {
    deletePost.mockResolvedValue(undefined)
    await openFromHome(post({ ownedByMe: true }))
    openDeleteConfirm()

    fireEvent.click(screen.getByRole('button', { name: '削除する' }))

    await screen.findByText('プロフィール画面')
    expect(deletePost).toHaveBeenCalledWith(1)
    // 詳細の履歴はプロフィールに置き換わっているため、戻るとホームに戻る
    fireEvent.click(screen.getByRole('button', { name: 'ブラウザの戻る' }))
    expect(await screen.findByText('ホーム画面')).toBeTruthy()
  })

  it('削除の通信中は、もう一度押せず、キャンセルもできない', async () => {
    deletePost.mockReturnValue(new Promise(() => {}))
    await openFromHome(post({ ownedByMe: true }))
    openDeleteConfirm()

    fireEvent.click(screen.getByRole('button', { name: '削除する' }))

    expect(screen.getByRole('button', { name: '削除しています...' }).hasAttribute('disabled')).toBe(true)
    expect(screen.getByRole('button', { name: 'キャンセル' }).hasAttribute('disabled')).toBe(true)
    expect(deletePost).toHaveBeenCalledTimes(1)
  })

  it('削除に失敗したら、確認の中にエラーを出して画面に留まる', async () => {
    deletePost.mockRejectedValue(new ApiError(403, null, 'この投稿は削除できません。'))
    await openFromHome(post({ ownedByMe: true }))
    openDeleteConfirm()

    fireEvent.click(screen.getByRole('button', { name: '削除する' }))

    const dialog = screen.getByRole('alertdialog')
    expect(await screen.findByText('この投稿は削除できません。')).toBeTruthy()
    expect(dialog.contains(screen.getByText('この投稿は削除できません。'))).toBe(true)
    expect(screen.queryByText('プロフィール画面')).toBeNull()
    expect(screen.getByRole('button', { name: '削除する' }).hasAttribute('disabled')).toBe(false)
  })

  it('すでに削除されていた（404）なら、削除できたものとしてプロフィールへ移る', async () => {
    deletePost.mockRejectedValue(new ApiError(404, null, '投稿が見つかりません。'))
    await openFromHome(post({ ownedByMe: true }))
    openDeleteConfirm()

    fireEvent.click(screen.getByRole('button', { name: '削除する' }))

    expect(await screen.findByText('プロフィール画面')).toBeTruthy()
  })
})

describe('投稿の編集', () => {
  it('自分の投稿では、メニューの「削除」の上に「編集」があり、押すと編集画面へ移る', async () => {
    fetchPost.mockResolvedValue(post({ ownedByMe: true }))
    render(
      <MemoryRouter initialEntries={['/posts/1']}>
        <Routes>
          <Route path="/posts/:postId" element={<PostDetailPage />} />
          <Route path="/posts/:postId/edit" element={<p>編集画面</p>} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByText('@haru_st')

    fireEvent.click(screen.getByRole('button', { name: '投稿のメニュー' }))
    const items = screen.getAllByRole('menuitem').map((item) => item.textContent)
    expect(items).toEqual(['編集', '削除'])
    fireEvent.click(screen.getByRole('menuitem', { name: '編集' }))

    expect(await screen.findByText('編集画面')).toBeTruthy()
  })

  it('他人の投稿には、編集のメニューを出さない', async () => {
    await open(post({ ownedByMe: false }))

    expect(screen.queryByRole('menuitem', { name: '編集' })).toBeNull()
  })

  it('編集した投稿は、投稿日の横に「編集済み」を出す', async () => {
    const { container } = await open(post({ editedAt: '2026-10-09T08:00:00' }))

    expect(container.querySelector('.post-detail-edited')?.textContent).toBe('編集済み')
  })

  it('編集していない投稿には「編集済み」を出さない', async () => {
    const { container } = await open(post())

    expect(container.querySelector('.post-detail-edited')).toBeNull()
    expect(container.textContent).not.toContain('編集済み')
  })
})

describe('他人の投稿の「…」（通報・ブロック）', () => {
  it('他人の投稿の「…」には「通報」と「ブロック」がある', async () => {
    await open(post({ ownedByMe: false }))

    fireEvent.click(screen.getByRole('button', { name: '投稿のメニュー' }))

    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual(['通報', 'ブロック'])
  })

  it('「通報」で、この投稿を対象にした通報の入力を出す', async () => {
    await open(post({ ownedByMe: false }))

    fireEvent.click(screen.getByRole('button', { name: '投稿のメニュー' }))
    fireEvent.click(screen.getByRole('menuitem', { name: '通報' }))

    const dialog = screen.getByRole('dialog', { name: '投稿を通報' })
    expect(within(dialog).getByLabelText('他人の写真の無断転載')).toBeTruthy()
  })

  it('「ブロック」で投稿者のブロックを確かめ、ブロックしたらホームへ移る', async () => {
    blockUser.mockResolvedValue(undefined)
    await openFromHome(post({ ownedByMe: false }))

    fireEvent.click(screen.getByRole('button', { name: '投稿のメニュー' }))
    fireEvent.click(screen.getByRole('menuitem', { name: 'ブロック' }))
    const confirm = screen.getByRole('alertdialog')
    expect(confirm.textContent).toContain('@haru_st さんをブロックしますか？')
    fireEvent.click(within(confirm).getByRole('button', { name: 'ブロックする' }))

    expect(await screen.findByText('ホーム画面')).toBeTruthy()
    expect(blockUser).toHaveBeenCalledWith(2)
  })
})
