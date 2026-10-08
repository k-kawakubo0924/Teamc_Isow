import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PostResponse } from '../../api/posts'
import PostDetailPage from './PostDetailPage'

// API とログイン状態は差し替える（画面の表示だけを確かめる）
const fetchPost = vi.fn<(id: number) => Promise<PostResponse>>()
vi.mock('../../api/posts', () => ({ fetchPost: (id: number) => fetchPost(id) }))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token' }) }))

afterEach(() => {
  cleanup()
  fetchPost.mockReset()
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
    createdAt: '2026-10-08T12:00:00',
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
