import type { Location, NavigateFunction } from 'react-router'

/**
 * 1つ前の画面に戻る。URL を直接開いた場合など、アプリ内に戻る先が無いときは fallback に移動する
 * （react-router は最初に開いた画面の location.key を 'default' にする）
 */
export function goBack(navigate: NavigateFunction, location: Location, fallback: string): void {
  if (location.key === 'default') {
    navigate(fallback, { replace: true })
  } else {
    navigate(-1)
  }
}
