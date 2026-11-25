// バックエンドのURLは環境変数で管理する（.env.example 参照）
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/**
 * バックエンドのエラー応答（ApiErrorResponse）。errors は項目名 → その欄の下に出す警告文。
 * reason は DM・相談のエラー（DmErrorResponse）と利用停止（ACCOUNT_SUSPENDED）にある、理由の定数名（LIMIT_REACHED など）
 */
export type ApiErrorBody = {
  message: string
  errors: Record<string, string>
  reason?: string
}

/** 利用停止の理由と期限（バックエンドの SuspendedErrorResponse。docs/admin.md「6. 措置」） */
export type SuspensionInfo = {
  /** 期限。無期限なら null */
  suspendedUntil: string | null
  /** 理由の表示名（「嫌がらせ・誹謗中傷」など） */
  reasonLabel: string | null
  /**
   * 申し立て用の鍵（docs/settings.md「お問い合わせ」）。利用停止の画面から申し立てるときに送る。
   * 保存せず、メモリにだけ持つ（再読み込みで消えたら、もう一度ログインすれば受け取れる）
   */
  appealToken: string | null
}

/** 利用停止中のユーザーへの応答の reason（ログイン・すべての API で同じ） */
const ACCOUNT_SUSPENDED = 'ACCOUNT_SUSPENDED'

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

/*
 * 失敗の文言は、種類ごとに分ける（利用者が「やり直せばよいのか」「入力が悪いのか」を見分けられるように）。
 * - 接続できない：CONNECTION_ERROR_MESSAGE
 * - 入力の誤り（400 で欄ごとの誤りがある）：INPUT_ERROR_MESSAGE（欄ごとの誤りは body.errors で欄の下に出すため、「赤い欄を修正してください」がそのまま通る）
 * - 本文に message がない失敗（404・500 など）：SERVER_PROBLEM_MESSAGE
 * - それ以外（本文に message がある 400・401・403・409・429 など）：サーバーの文言をそのまま
 * 以前は本文に message がない失敗をすべて「エラーが発生しました。時間をおいて再度お試しください。」にしていたため、
 * API が存在しない（404）ことに気づくのが遅れた。すべてを1つの文言に戻さないこと
 */
const CONNECTION_ERROR_MESSAGE = 'サーバーに接続できませんでした。時間をおいて再度お試しください。'
// 画面の側で入力を確かめたときの文言（validation/authRules.ts・postRules.ts の INVALID_INPUT_MESSAGE）とそろえる
const INPUT_ERROR_MESSAGE = '入力内容に誤りがあります。赤い欄を修正してください。'
const SERVER_PROBLEM_MESSAGE = 'サーバーで問題が起きました。時間をおいてやり直してください。'

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
let suspendedHandler: ((info: SuspensionInfo) => void) | null = null

/**
 * トークン付きのリクエストが 401 になったとき（期限切れ・退会など）に呼ぶ処理を登録する。
 * AuthProvider がログアウト処理を登録するので、各画面で 401 を処理する必要はない。
 */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

/**
 * API が「利用停止中」（403・reason が ACCOUNT_SUSPENDED）を返したときに呼ぶ処理を登録する（docs/admin.md「6. 措置」）。
 * ログインの API も含め、どの API でも呼ぶ。AuthProvider がログアウトと利用停止の画面への切り替えを登録するので、
 * 各画面で利用停止を処理する必要はない
 */
export function setSuspendedHandler(handler: ((info: SuspensionInfo) => void) | null): void {
  suspendedHandler = handler
}

/**
 * 認証付きで中身（画像など）を取得する（DM の画像・証拠の写真。docs/dm.md「画像」）。
 * <img> のタグは認証のヘッダーを付けられないため、中身を取得してから、その場限りの URL にして表示する。
 * 借りた端末・共用の端末に残さないよう、ブラウザにキャッシュさせない（no-store）。失敗の扱い（401 でのログアウトなど）はほかの API と同じ
 */
export async function getBlob(path: string, token: string, signal?: AbortSignal): Promise<Blob> {
  const res = await send(path, { method: 'GET', headers: { Authorization: `Bearer ${token}` }, cache: 'no-store', signal })
  return res.blob()
}

async function request<T>(path: string, init: RequestInit): Promise<T> {
  const res = await send(path, init)
  // 本文のない成功（204。検索履歴の削除など）は undefined を返す。呼び出し側は T に void を指定すること
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

/** リクエストを送り、成功した応答を返す。失敗は ApiError にする（401・利用停止の処理もここで行う） */
async function send(path: string, init: RequestInit): Promise<Response> {
  let res: Response
  try {
    res = await fetch(`${API_BASE_URL}${path}`, init)
  } catch (err) {
    // 呼び出し側が中断した場合は、接続エラーではなく中断として扱えるよう、そのまま投げる
    if (err instanceof DOMException && err.name === 'AbortError') throw err
    throw new ApiError(0, null, CONNECTION_ERROR_MESSAGE)
  }

  if (res.ok) return res

  // ログインAPIの 401（認証失敗）は対象外にするため、トークンを付けたリクエストのみ扱う
  const sentToken = new Headers(init.headers).has('Authorization')
  if (res.status === 401 && sentToken) {
    unauthorizedHandler?.()
  }

  const json = await readJson(res)
  if (res.status === 403 && isSuspended(json)) {
    suspendedHandler?.({
      suspendedUntil: typeof json.suspendedUntil === 'string' ? json.suspendedUntil : null,
      reasonLabel: typeof json.suspensionReasonLabel === 'string' ? json.suspensionReasonLabel : null,
      appealToken: typeof json.appealToken === 'string' ? json.appealToken : null,
    })
  }

  const errorBody = toErrorBody(json)
  throw new ApiError(res.status, errorBody, failureMessage(res.status, errorBody))
}

/** 画面に出す失敗の文言（上のコメントの分け方） */
function failureMessage(status: number, body: ApiErrorBody | null): string {
  if (body === null) return SERVER_PROBLEM_MESSAGE
  if (status === 400 && Object.keys(body.errors).length > 0) return INPUT_ERROR_MESSAGE
  return body.message
}

/** エラー応答の本文。本文が空、または JSON でない場合は null */
async function readJson(res: Response): Promise<unknown> {
  try {
    return await res.json()
  } catch {
    return null
  }
}

function isSuspended(json: unknown): json is Record<string, unknown> {
  return typeof json === 'object' && json !== null && 'reason' in json && json.reason === ACCOUNT_SUSPENDED
}

/** エラー応答が ApiErrorBody の形でない場合（500 など）は null を返す */
function toErrorBody(json: unknown): ApiErrorBody | null {
  if (typeof json === 'object' && json !== null && 'message' in json && typeof json.message === 'string') {
    const errors = 'errors' in json && typeof json.errors === 'object' && json.errors !== null ? json.errors : {}
    const reason = 'reason' in json && typeof json.reason === 'string' ? json.reason : undefined
    return { message: json.message, errors: errors as Record<string, string>, reason }
  }
  return null
}
