// バックエンドのURLは環境変数で管理する（.env.example 参照）
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/** バックエンドのエラー応答（ApiErrorResponse）。errors は項目名 → その欄の下に出す警告文 */
export type ApiErrorBody = {
  message: string
  errors: Record<string, string>
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

export function postJson<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

/** token を渡すと Authorization: Bearer ヘッダーを付ける（認証が必要なAPI用） */
export function getJson<T>(path: string, token?: string | null): Promise<T> {
  return request<T>(path, {
    method: 'GET',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
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
  } catch {
    throw new ApiError(0, null, CONNECTION_ERROR_MESSAGE)
  }

  if (res.ok) {
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
      return { message: json.message, errors: errors as Record<string, string> }
    }
  } catch {
    // 本文が空、または JSON でない
  }
  return null
}
