// 認証付きで取得した画像（DM の画像・証拠の写真）の控え（docs/dm.md「画像」）。
//
// 画像は、ブラウザにはキャッシュさせない（no-store）。借りた端末・共用の端末に DM の写真が残るのは、
// まさになくしたい状態のため。その代わり、画面を開いている間だけメモリに控え、同じ画像を何度も取りに行かない
// （5秒ごとの取り直しで一覧が作り直されても、チャットを開き直しても、取り直さない）。
// 控えは、ログアウト・トークンが変わったとき（別の人がログインしたとき）・再読み込みで消える。

/** 控える画像の数の上限。超えたら、長く使っていないものから捨てる（メモリを使い続けないため） */
export const MAX_CACHED_IMAGES = 100

type Entry = {
  /** その場限りの URL（URL.createObjectURL）。取得中は null */
  url: string | null
  promise: Promise<string>
}

// Map は入れた順を保つため、使うたびに入れ直して「長く使っていない順」にする
const entries = new Map<string, Entry>()
let cachedToken: string | null = null
/** 捨てるたびに進める。捨てる前に取りに行ったものが届いても、控えないため */
let generation = 0

/**
 * key の画像を、その場限りの URL にして返す。控えがあれば取りに行かない。
 *
 * @param key 画像を見分ける名前（API のパスなど）
 * @param load 中身を取得する（認証付きの API。失敗したら例外）。失敗したものは控えず、次に頼まれたら取り直す
 */
export function loadAuthorizedImage(key: string, token: string, load: (token: string) => Promise<Blob>): Promise<string> {
  if (cachedToken !== token) {
    clearAuthorizedImages()
    cachedToken = token
  }
  const found = entries.get(key)
  if (found) {
    entries.delete(key)
    entries.set(key, found)
    return found.promise
  }

  const startedAt = generation
  const entry: Entry = { url: null, promise: Promise.resolve('') }
  entry.promise = load(token).then(
    (blob) => {
      const url = URL.createObjectURL(blob)
      if (startedAt !== generation) {
        // 取りに行っている間に捨てられた（ログアウトなど）。届いたものも控えない
        URL.revokeObjectURL(url)
        throw new Error('画像の控えを捨てた後に届きました')
      }
      entry.url = url
      return url
    },
    (err: unknown) => {
      if (entries.get(key) === entry) entries.delete(key)
      throw err
    },
  )
  entries.set(key, entry)
  evictOverLimit()
  return entry.promise
}

/** 控えをすべて捨て、その場限りの URL を解放する（ログアウトのとき） */
export function clearAuthorizedImages(): void {
  generation++
  for (const entry of entries.values()) {
    if (entry.url) URL.revokeObjectURL(entry.url)
  }
  entries.clear()
  cachedToken = null
}

function evictOverLimit() {
  while (entries.size > MAX_CACHED_IMAGES) {
    const [oldestKey, oldest] = entries.entries().next().value as [string, Entry]
    entries.delete(oldestKey)
    if (oldest.url) URL.revokeObjectURL(oldest.url)
  }
}
