import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearAuthorizedImages, loadAuthorizedImage, MAX_CACHED_IMAGES } from './authorizedImageCache'

/*
 * 認証付きで取得した画像の控え（DM の画像・証拠の写真。docs/dm.md「画像」）。
 * ブラウザには残さない（no-store）代わりに、画面を開いている間だけメモリに控え、同じ画像を何度も取りに行かない。
 * 借りた端末・共用の端末に DM の写真が残らないよう、ログアウト・再読み込みで消える
 */

let created = 0
const createObjectURL = vi.fn(() => `blob:image-${++created}`)
const revokeObjectURL = vi.fn()

beforeEach(() => {
  created = 0
  createObjectURL.mockClear()
  revokeObjectURL.mockClear()
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL, revokeObjectURL }))
})

afterEach(() => {
  clearAuthorizedImages()
  vi.unstubAllGlobals()
})

function blob() {
  return new Blob(['x'], { type: 'image/png' })
}

describe('loadAuthorizedImage', () => {
  it('取得した中身を、その場限りの URL にして返す', async () => {
    const load = vi.fn(async () => blob())

    expect(await loadAuthorizedImage('dm/10/5', 'token', load)).toBe('blob:image-1')
    expect(load).toHaveBeenCalledWith('token')
  })

  it('同じ画像は、2回目からは取りに行かない（同時に頼まれても1回だけ取りに行く）', async () => {
    const load = vi.fn(async () => blob())

    const [first, second] = await Promise.all([
      loadAuthorizedImage('dm/10/5', 'token', load),
      loadAuthorizedImage('dm/10/5', 'token', load),
    ])
    const third = await loadAuthorizedImage('dm/10/5', 'token', load)

    expect(load).toHaveBeenCalledTimes(1)
    expect(new Set([first, second, third]).size).toBe(1)
  })

  it('取得に失敗したものは控えず、次に頼まれたら取り直す', async () => {
    const load = vi.fn<(token: string) => Promise<Blob>>()
    load.mockRejectedValueOnce(new Error('failed')).mockResolvedValueOnce(blob())

    await expect(loadAuthorizedImage('dm/10/5', 'token', load)).rejects.toThrow()
    expect(await loadAuthorizedImage('dm/10/5', 'token', load)).toBe('blob:image-1')
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('トークンが変わったら（別の人がログインしたら）、それまでの控えを捨てて使わない', async () => {
    const load = vi.fn(async () => blob())
    await loadAuthorizedImage('dm/10/5', 'token-a', load)

    await loadAuthorizedImage('dm/10/5', 'token-b', load)

    expect(load).toHaveBeenCalledTimes(2)
    expect(load).toHaveBeenLastCalledWith('token-b')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:image-1')
  })

  it('控えは上限まで。超えたら、長く使っていないものから捨てる', async () => {
    const load = vi.fn(async () => blob())
    for (let i = 0; i < MAX_CACHED_IMAGES; i++) {
      await loadAuthorizedImage(`dm/10/${i}`, 'token', load)
    }
    // 最初の画像を使い直すと、捨てる順番の後ろに回る
    await loadAuthorizedImage('dm/10/0', 'token', load)

    await loadAuthorizedImage('dm/10/new', 'token', load)

    expect(revokeObjectURL).toHaveBeenCalledTimes(1)
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:image-2')
    load.mockClear()
    await loadAuthorizedImage('dm/10/0', 'token', load)
    expect(load).not.toHaveBeenCalled()
  })
})

describe('clearAuthorizedImages', () => {
  it('控えをすべて捨て、URL を解放する。次に頼まれたら取り直す', async () => {
    const load = vi.fn(async () => blob())
    await loadAuthorizedImage('dm/10/5', 'token', load)
    await loadAuthorizedImage('dm/10/6', 'token', load)

    clearAuthorizedImages()

    expect(revokeObjectURL).toHaveBeenCalledWith('blob:image-1')
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:image-2')
    await loadAuthorizedImage('dm/10/5', 'token', load)
    expect(load).toHaveBeenCalledTimes(3)
  })

  it('取得の途中で捨てた画像は、届いても控えない（ログアウトした後に残さない）', async () => {
    let resolve: (value: Blob) => void = () => {}
    const load = vi.fn(() => new Promise<Blob>((r) => (resolve = r)))
    const pending = loadAuthorizedImage('dm/10/5', 'token', load)

    clearAuthorizedImages()
    resolve(blob())
    await pending.catch(() => {})

    expect(revokeObjectURL).toHaveBeenCalledWith('blob:image-1')
    await loadAuthorizedImage('dm/10/5', 'token', vi.fn(async () => blob()))
    expect(createObjectURL).toHaveBeenCalledTimes(2)
  })
})
