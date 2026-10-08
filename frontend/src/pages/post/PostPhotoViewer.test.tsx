import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { PostPhotoViewer } from './PostPhotoViewer'

afterEach(cleanup)

describe('PostPhotoViewer（投稿の写真。確認画面・詳細画面で共通）', () => {
  it('写真が1枚なら「1 / 1」の枚数表示も ‹ › のボタンも出さない', () => {
    const { container } = render(<PostPhotoViewer urls={['http://localhost/uploads/a.jpg']} />)

    expect(screen.getByRole('img').getAttribute('src')).toBe('http://localhost/uploads/a.jpg')
    expect(container.textContent).not.toContain('/')
    expect(container.querySelector('.post-photo-count')).toBeNull()
    expect(screen.queryByRole('button', { name: '前の写真' })).toBeNull()
    expect(screen.queryByRole('button', { name: '次の写真' })).toBeNull()
  })

  it('写真が2枚以上なら枚数表示とボタンを出し、ボタンで切り替えられる', () => {
    render(<PostPhotoViewer urls={['http://localhost/uploads/a.jpg', 'http://localhost/uploads/b.jpg']} />)

    expect(screen.getByText('1 / 2')).toBeTruthy()
    const prev = screen.getByRole('button', { name: '前の写真' }) as HTMLButtonElement
    const next = screen.getByRole('button', { name: '次の写真' }) as HTMLButtonElement
    expect(prev.disabled).toBe(true)

    fireEvent.click(next)

    expect(screen.getByText('2 / 2')).toBeTruthy()
    expect(screen.getByRole('img').getAttribute('src')).toBe('http://localhost/uploads/b.jpg')
    expect(next.disabled).toBe(true)
    expect(prev.disabled).toBe(false)
  })
})
