// 投稿作成の入力規則。
// バックエンドの PostCreateRequest / TagService / TagNameNormalizer / HttpUrlValidator / ImageUploadService と
// 同じ規則にしている（変更時は両方を直すこと）。文字数などの上限は docs/post.md の暫定値。
// 最終的な判定はバックエンドが行う。ここでのチェックは、送信前に早めに知らせるためのもの。

export const MAX_IMAGES = 10
/** 1ファイルあたりの上限。超えるファイルは送らない（大きく超えると、サーバーのエラー本文が届かないため） */
export const MAX_IMAGE_BYTES = 10 * 1024 * 1024
export const MAX_TITLE_LENGTH = 100
export const MAX_TAGS = 10
export const MAX_TAG_LENGTH = 30
export const MAX_WORN_ITEMS_LENGTH = 1000
export const MAX_DESCRIPTION_LENGTH = 2000
export const MAX_REFERENCE_URL_LENGTH = 2048

/** バックエンドで受け付ける画像の形式（拡張子と MIME タイプの組） */
const IMAGE_TYPES: { mimeType: string; extensions: string[] }[] = [
  { mimeType: 'image/jpeg', extensions: ['jpg', 'jpeg'] },
  { mimeType: 'image/png', extensions: ['png'] },
]

/** ファイル選択で表示する形式（input の accept 属性） */
export const IMAGE_ACCEPT = 'image/jpeg,image/png'

export const INVALID_INPUT_MESSAGE = '入力内容に誤りがあります。赤い欄を修正してください。'

export type PostForm = {
  title: string
  fashionCategoryId: number | null
  tags: string[]
  wornItems: string
  description: string
  referenceUrl: string
}

export type PostField = 'images' | keyof PostForm

export type PostFieldErrors = Partial<Record<PostField, string>>

/**
 * 選んだ画像1枚の確認。問題があれば警告文、なければ null。
 * 中身が本当に画像かどうかはバックエンドで確認する
 */
export function validateImageFile(file: File): string | null {
  const extension = file.name.includes('.') ? file.name.slice(file.name.lastIndexOf('.') + 1).toLowerCase() : ''
  const type = IMAGE_TYPES.find((t) => t.mimeType === file.type)
  if (!type || !type.extensions.includes(extension)) {
    return `「${file.name}」は選択できません。JPEG または PNG の画像を選択してください。`
  }
  if (file.size > MAX_IMAGE_BYTES) {
    return `「${file.name}」は大きすぎます。画像は1枚あたり ${MAX_IMAGE_BYTES / 1024 / 1024}MB 以下にしてください。`
  }
  return null
}

/**
 * タグ名を表示名の形に整える（バックエンドの TagNameNormalizer.displayName と同じ）。
 * 全角英数字を半角・半角カナを全角にし（NFKC）、前後の空白を除き、途中の連続した空白を1つにまとめ、先頭の # を除く
 */
export function normalizeTagName(value: string): string {
  let normalized = value.normalize('NFKC').trim().replace(/\s+/g, ' ')
  if (normalized.startsWith('#')) {
    normalized = normalized.slice(1).trim()
  }
  return normalized
}

/** 同じタグかどうかの判定に使う値（バックエンドの TagNameNormalizer.key と同じ）。Y2K と y2k は同じタグ */
export function tagKey(value: string): string {
  return normalizeTagName(value).toLowerCase()
}

/** 手入力したタグを追加できるか。問題があれば警告文、なければ null（すでに追加済みかは呼び出し側で確認する） */
export function validateNewTag(name: string, currentTags: string[]): string | null {
  const normalized = normalizeTagName(name)
  if (normalized === '') return 'タグを入力してください'
  if (normalized.length > MAX_TAG_LENGTH) return `タグは1つ${MAX_TAG_LENGTH}文字以内で入力してください`
  if (currentTags.length >= MAX_TAGS) return `タグは${MAX_TAGS}個まで設定できます`
  return null
}

// 日本語を含む URL（https://example.com/商品 など）も受け付ける（バックエンドの HttpUrlValidator と同じ）
const HTTP_URL = /^https?:\/\/[^\s/?#@]+(?:[/?#]\S*)?$/i

/** 送信前の確認。写真の枚数と、文字の項目・選択の項目を確認する */
export function validatePost(form: PostForm, imageCount: number): PostFieldErrors {
  const errors: PostFieldErrors = {}
  const title = form.title.trim()
  const wornItems = form.wornItems.trim()
  const description = form.description.trim()
  const referenceUrl = form.referenceUrl.trim()

  if (imageCount === 0) {
    errors.images = '写真を1枚以上選択してください'
  } else if (imageCount > MAX_IMAGES) {
    errors.images = `写真は${MAX_IMAGES}枚まで選択できます`
  }

  if (title === '') {
    errors.title = '題名を入力してください'
  } else if (title.length > MAX_TITLE_LENGTH) {
    errors.title = `題名は${MAX_TITLE_LENGTH}文字以内で入力してください`
  }

  if (form.fashionCategoryId === null) {
    errors.fashionCategoryId = 'ファッションの種類を選択してください'
  }

  if (form.tags.length === 0) {
    errors.tags = 'タグを1つ以上設定してください'
  } else if (form.tags.length > MAX_TAGS) {
    errors.tags = `タグは${MAX_TAGS}個まで設定できます`
  }

  if (wornItems.length > MAX_WORN_ITEMS_LENGTH) {
    errors.wornItems = `着用アイテムは${MAX_WORN_ITEMS_LENGTH}文字以内で入力してください`
  }

  if (description === '') {
    errors.description = '投稿説明を入力してください'
  } else if (description.length > MAX_DESCRIPTION_LENGTH) {
    errors.description = `投稿説明は${MAX_DESCRIPTION_LENGTH}文字以内で入力してください`
  }

  if (referenceUrl.length > MAX_REFERENCE_URL_LENGTH) {
    errors.referenceUrl = `参考情報のURLは${MAX_REFERENCE_URL_LENGTH}文字以内で入力してください`
  } else if (referenceUrl !== '' && !HTTP_URL.test(referenceUrl)) {
    errors.referenceUrl = '参考情報は http:// または https:// で始まるURLを入力してください'
  }

  return errors
}
