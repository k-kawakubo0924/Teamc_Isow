import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchEvidenceImage } from './admin'
import { setUnauthorizedHandler } from './client'

/*
 * 証拠の写真の取得（docs/admin.md「通報」の「証拠の写真の配信」）。
 * /uploads ではなく管理者向けの API から、ログインのトークンを付けて取得し、ブラウザに残さない
 */

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('fetchEvidenceImage', () => {
  it('管理者向けの API を、トークンを付け、キャッシュさせずに呼ぶ', async () => {
    const fetchMock = vi.fn(async () => new Response('x', { status: 200, headers: { 'Content-Type': 'image/png' } }))
    vi.stubGlobal('fetch', fetchMock)

    const blob = await fetchEvidenceImage(7, 101, 'admin-token')

    expect(blob.size).toBe(1)
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit]
    expect(url).toMatch(/\/api\/admin\/reports\/7\/evidence-files\/101$/)
    expect(url).not.toContain('/uploads')
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer admin-token')
    expect(init.cache).toBe('no-store')
  })

  it('取得できなければ例外にする', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 404 })))

    await expect(fetchEvidenceImage(7, 101, 'admin-token')).rejects.toThrow()
  })

  it('401 なら、ほかの API と同じくログアウトの処理を呼ぶ（DM の画像と同じ取得の部品を通す。docs/dm.md「画像」）', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 401 })))

    await expect(fetchEvidenceImage(7, 101, 'admin-token')).rejects.toThrow()
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
    setUnauthorizedHandler(null)
  })
})
