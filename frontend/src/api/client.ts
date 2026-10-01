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

export async function postJson<T>(path: string, body: unknown): Promise<T> {
  let res: Response
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, null, CONNECTION_ERROR_MESSAGE)
  }

  if (res.ok) {
    return (await res.json()) as T
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
