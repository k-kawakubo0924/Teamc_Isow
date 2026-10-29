import { describe, expect, it } from 'vitest'
import { MAX_IMAGE_BYTES, validateImageFile } from './postRules'

/*
 * 送信前の画像の確認（投稿・プロフィール画像・DM で共通）。上限はバックエンドの app.image.max-file-size（9MB）と同じ。
 * 写真10枚の投稿が、公開時の入口（Cloudflare）の1リクエスト 100MB に収まる大きさにしている（docs/post.md「写真の大きさ」）
 */

function fileOfSize(name: string, type: string, bytes: number): File {
  const file = new File([], name, { type })
  Object.defineProperty(file, 'size', { value: bytes })
  return file
}

describe('validateImageFile', () => {
  it('1枚あたりの上限は 9MB', () => {
    expect(MAX_IMAGE_BYTES).toBe(9 * 1024 * 1024)
  })

  it('9MB ちょうどの写真は送れる', () => {
    expect(validateImageFile(fileOfSize('photo.jpg', 'image/jpeg', MAX_IMAGE_BYTES))).toBeNull()
  })

  it('9MB を超える写真は、上限と、どうすればよいかを伝える', () => {
    expect(validateImageFile(fileOfSize('photo.jpg', 'image/jpeg', MAX_IMAGE_BYTES + 1))).toBe(
      '「photo.jpg」は大きすぎます（1枚あたり9MBまで）。写真アプリなどで画像を小さくしてから、もう一度選択してください。',
    )
  })
})
