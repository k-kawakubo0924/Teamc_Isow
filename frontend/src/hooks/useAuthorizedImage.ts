import { useEffect, useRef, useState } from 'react'
import { loadAuthorizedImage } from './authorizedImageCache'

/**
 * 認証付きで取得した画像を、その場限りの URL にして返す（DM の画像・証拠の写真。docs/dm.md「画像」）。
 * <img> のタグは認証のヘッダーを付けられないため、中身を取得してから表示する。画面を開いている間は控えを使う（authorizedImageCache）。
 * 取り直しは key・token が変わったときだけ（load は毎回作り直されても取り直さない）
 *
 * @param key 画像を見分ける名前（API のパスなど）
 * @param load 中身を取得する（認証付きの API）
 */
export function useAuthorizedImage(
  key: string,
  token: string | null,
  load: (token: string) => Promise<Blob>,
): { url: string | null; failed: boolean } {
  const [state, setState] = useState<{ key: string; url: string | null; failed: boolean }>({
    key,
    url: null,
    failed: false,
  })
  const loadRef = useRef(load)
  useEffect(() => {
    loadRef.current = load
  })

  useEffect(() => {
    if (token === null) return
    let cancelled = false
    loadAuthorizedImage(key, token, (t) => loadRef.current(t))
      .then((url) => {
        if (!cancelled) setState({ key, url, failed: false })
      })
      .catch(() => {
        if (!cancelled) setState({ key, url: null, failed: true })
      })
    return () => {
      cancelled = true
    }
  }, [key, token])

  // key が変わった直後は、前の画像を出さない
  return state.key === key ? { url: state.url, failed: state.failed } : { url: null, failed: false }
}
