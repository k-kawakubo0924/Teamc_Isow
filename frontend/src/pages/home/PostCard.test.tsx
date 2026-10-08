import { cleanup, render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import type { TimelineItem } from '../../api/posts'
import { PostCard } from './PostCard'

afterEach(cleanup)

function item(heightCm: number | null | undefined): TimelineItem {
  return {
    id: 1,
    thumbnailUrl: 'http://localhost/uploads/a.jpg',
    fashionCategory: { id: 1, name: 'きれいめ' },
    author: { id: 2, username: 'haru_st', heightCm: heightCm as number | null },
    likeCount: 0,
    likedByMe: false,
    favoritedByMe: false,
    createdAt: '2026-10-08T12:00:00',
  }
}

function renderCard(post: TimelineItem) {
  return render(
    <MemoryRouter>
      <PostCard post={post} detailed onToggleLike={() => {}} onToggleFavorite={() => {}} />
    </MemoryRouter>,
  )
}

describe('投稿のカード（ホーム・プロフィール・検索結果）', () => {
  it.each([null, undefined])('身長が未設定（%s）なら、ファッションの種類だけを出す', (heightCm) => {
    const { container } = renderCard(item(heightCm))

    expect(container.querySelector('.home-card-meta')?.textContent).toBe('きれいめ')
    expect(container.textContent).not.toMatch(/nullcm|undefinedcm/)
  })

  it('身長が設定されていれば「160cm ・ きれいめ」', () => {
    const { container } = renderCard(item(160))

    expect(container.querySelector('.home-card-meta')?.textContent).toBe('160cm ・ きれいめ')
  })

  it('写真を押すと投稿の詳細画面へ移るリンクになっている', () => {
    const { container } = renderCard(item(160))

    expect(container.querySelector('.home-card-photo-link')?.getAttribute('href')).toBe('/posts/1')
  })
})
