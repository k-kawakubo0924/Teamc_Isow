import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './authContext'

/*
 * ログアウトしたら、画面を開いている間だけ控えていた画像（DM の画像・証拠の写真）を捨てる（docs/dm.md「画像」）。
 * 借りた端末・共用の端末で、次に使う人の手元に DM の写真を残さないため
 */

const clearAuthorizedImages = vi.fn()
vi.mock('../hooks/authorizedImageCache', () => ({ clearAuthorizedImages: () => clearAuthorizedImages() }))

afterEach(() => {
  cleanup()
  clearAuthorizedImages.mockReset()
  localStorage.clear()
})

function LogoutButton() {
  const { logout } = useAuth()
  return (
    <button type="button" onClick={logout}>
      ログアウト
    </button>
  )
}

describe('ログアウトと画像の控え', () => {
  it('ログアウトすると、画像の控えを捨てる', () => {
    render(
      <AuthProvider>
        <LogoutButton />
      </AuthProvider>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'ログアウト' }))

    expect(clearAuthorizedImages).toHaveBeenCalled()
  })
})
