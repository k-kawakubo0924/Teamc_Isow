import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, getJson, postJson } from './client'

/*
 * API の失敗を画面に出す文言（共通の部品 client.ts）の確認。
 * 失敗の種類ごとに文言を分ける。以前は本文に message のない失敗（404・500 など）をすべて
 * 「エラーが発生しました。時間をおいて再度お試しください。」にしていたため、API が存在しない（404）ことに気づくのが遅れた
 */

function stubFetch(status: number, body: string, contentType = 'application/json') {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response(body, { status, headers: { 'Content-Type': contentType } })),
  )
}

async function failure(call: () => Promise<unknown>): Promise<ApiError> {
  try {
    await call()
  } catch (err) {
    if (err instanceof ApiError) return err
    throw err
  }
  throw new Error('失敗しませんでした')
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('API の失敗の文言', () => {
  it('入力の誤り（400 で欄ごとの誤りがある）は、画面の側の入力の確かめと同じ文言を出し、欄ごとの誤りはそのまま渡す', async () => {
    stubFetch(400, JSON.stringify({ message: 'サーバーの文言', errors: { email: '形式が正しくありません' } }))

    const err = await failure(() => postJson('/api/x', {}))

    expect(err.status).toBe(400)
    expect(err.message).toBe('入力内容に誤りがあります。赤い欄を修正してください。')
    expect(err.body?.errors).toEqual({ email: '形式が正しくありません' })
  })

  it('欄ごとの誤りのない 400（リンクの期限切れなど）は、サーバーの文言をそのまま出す', async () => {
    stubFetch(400, JSON.stringify({ message: 'このリンクは使えません。', errors: {} }))

    expect((await failure(() => postJson('/api/x', {}))).message).toBe('このリンクは使えません。')
  })

  it('API が存在しない（404。Spring の標準の本文で message がない）は、サーバーで問題が起きたと出す', async () => {
    stubFetch(404, JSON.stringify({ timestamp: '2026-10-16T01:43:20.446Z', status: 404, error: 'Not Found', path: '/api/x' }))

    const err = await failure(() => postJson('/api/x', {}, 'token'))

    expect(err.status).toBe(404)
    expect(err.message).toBe('サーバーで問題が起きました。時間をおいてやり直してください。')
  })

  it('サーバーの内部エラー（500）・JSON でない本文も、サーバーで問題が起きたと出す', async () => {
    stubFetch(500, JSON.stringify({ status: 500, error: 'Internal Server Error' }))
    expect((await failure(() => getJson('/api/x', 'token'))).message).toBe('サーバーで問題が起きました。時間をおいてやり直してください。')

    stubFetch(502, '<html>Bad Gateway</html>', 'text/html')
    expect((await failure(() => getJson('/api/x', 'token'))).message).toBe('サーバーで問題が起きました。時間をおいてやり直してください。')
  })

  it('本文に message がある失敗（409・429 など）は、サーバーの文言をそのまま出す', async () => {
    stubFetch(429, JSON.stringify({ message: 'あと約20分で、もう一度申し込めます。', errors: {} }))

    expect((await failure(() => postJson('/api/x', {}, 'token'))).message).toBe('あと約20分で、もう一度申し込めます。')
  })

  it('サーバーに接続できないときは、今までどおり「サーバーに接続できませんでした」', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => Promise.reject(new TypeError('Failed to fetch'))))

    const err = await failure(() => getJson('/api/x'))

    expect(err.status).toBe(0)
    expect(err.message).toBe('サーバーに接続できませんでした。時間をおいて再度お試しください。')
  })
})
