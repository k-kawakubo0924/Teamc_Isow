// バックエンドのURLは環境変数で管理する（.env.example 参照）
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * バックエンドのエラー応答（ApiErrorResponse）。errors は項目名 → その欄の下に出す警告文。
 * reason は DM・相談のエラー（DmErrorResponse）だけにある、理由の定数名（LIMIT_REACHED など）
 */
export type ApiErrorBody = {
  message: string
  errors: Record<string, string>
  reason?: string
}

/** API呼び出しの失敗。status が 0 の場合はサーバーに接続できなかったことを表す */
export class ApiError extends Error {
  readonly status: number
  readonly body: ApiErrorBody | null

  constructor(status: number, body: ApiErrorBody | null, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

const CONNECTION_ERROR_MESSAGE = 'サーバーに接続できませんでした。時間をおいて再度お試しください。'
const UNEXPECTED_ERROR_MESSAGE = 'エラーが発生しました。時間をおいて再度お試しください。'

/** JSON を POST で送る。token を渡すと Authorization: Bearer ヘッダーを付ける（認証が必要なAPI用） */
export function postJson<T>(path: string, body: unknown, token?: string | null): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: token
      ? { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` }
      : { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/** JSON を PUT で送る（全項目を置き換える更新用。認証が必要） */
export function putJson<T>(path: string, body: unknown, token: string): Promise<T> {
  return request<T>(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
  })
}

/**
 * token を渡すと Authorization: Bearer ヘッダーを付ける（認証が必要なAPI用）。
 * signal を渡すと、途中で中断できる（入力中の候補検索で、古いリクエストを取り消すため）
 */
export function getJson<T>(path: string, token?: string | null, signal?: AbortSignal): Promise<T> {
  return request<T>(path, {
    method: 'GET',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    signal,
  })
}

/**
 * 本文なしで POST / DELETE を送り、JSON の応答を受け取る（いいね・お気に入りのように、URL だけで操作が決まる API 用）。
 * 応答が 204（本文なし）の API では T に void を指定する
 */
export function sendWithToken<T>(method: 'POST' | 'DELETE', path: string, token: string): Promise<T> {
  return request<T>(path, {
    method,
    headers: { Authorization: `Bearer ${token}` },
  })
}

/**
 * multipart/form-data で送る（画像ファイルを含む送信用）。
 * Content-Type はブラウザが境界文字列（boundary）付きで設定するため、指定しない
 */
export function postForm<T>(path: string, form: FormData, token: string): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
    body: form,
  })
}

let unauthorizedHandler: (() => void) | null = null

/**
 * トークン付きのリクエストが 401 になったとき（期限切れ・退会など）に呼ぶ処理を登録する。
 * AuthProvider がログアウト処理を登録するので、各画面で 401 を処理する必要はない。
 */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(`${API_BASE_URL}${path}`, init)
  } catch (err) {
    // 呼び出し側が中断した場合は、接続エラーではなく中断として扱えるよう、そのまま投げる
    if (err instanceof DOMException && err.name === 'AbortError') throw err
    throw new ApiError(0, null, CONNECTION_ERROR_MESSAGE)
  }

  if (res.ok) {
    // 本文のない成功（204。検索履歴の削除など）は undefined を返す。呼び出し側は T に void を指定すること
    if (res.status === 204) return undefined as T
    return (await res.json()) as T
  }

  // ログインAPIの 401（認証失敗）は対象外にするため、トークンを付けたリクエストのみ扱う
  const sentToken = new Headers(init.headers).has('Authorization')
  if (res.status === 401 && sentToken) {
    unauthorizedHandler?.()
  }

  const errorBody = await readErrorBody(res)
  throw new ApiError(res.status, errorBody, errorBody?.message ?? UNEXPECTED_ERROR_MESSAGE)
}

/** エラー応答が ApiErrorBody の形でない場合（500 など）は null を返す */
async function readErrorBody(res: Response): Promise<ApiErrorBody | null> {
  try {
    const json: unknown = await res.json()
    if (typeof json === 'object' && json !== null && 'message' in json && typeof json.message === 'string') {
      const errors = 'errors' in json && typeof json.errors === 'object' && json.errors !== null ? json.errors : {}
      const reason = 'reason' in json && typeof json.reason === 'string' ? json.reason : undefined
      return { message: json.message, errors: errors as Record<string, string>, reason }
    }
  } catch {
    // 本文が空、または JSON でない
  }
  return null
}
