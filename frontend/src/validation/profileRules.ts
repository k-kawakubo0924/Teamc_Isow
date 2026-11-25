/**
 * プロフィール編集の入力チェック（docs/profile.md）。
 * バックエンド（ProfileUpdateRequest）と同じ条件にそろえる。画面で先に確認し、送信前に誤りを伝えるため
 */

/** 名前の文字数の上限 */
export const MAX_DISPLAY_NAME_LENGTH = 50

/** 身長の範囲（暫定。docs/profile.md） */
export const MIN_HEIGHT_CM = 100
export const MAX_HEIGHT_CM = 250

export type ProfileForm = {
  displayName: string
  gender: string | null
  /** 入力欄の文字列のまま持つ。空なら未設定 */
  heightCm: string
  ageGroup: string | null
  bodyTypeId: number | null
  personalColorId: number | null
}

/** バックエンドの errors のキーと同じ。image はプロフィール画像 */
export type ProfileField = 'image' | keyof ProfileForm
export type ProfileFieldErrors = Partial<Record<ProfileField, string>>

export function validateProfile(form: ProfileForm): ProfileFieldErrors {
  const errors: ProfileFieldErrors = {}
  const name = form.displayName.trim()
  if (name === '') {
    errors.displayName = '名前を入力してください'
  } else if (name.length > MAX_DISPLAY_NAME_LENGTH) {
    errors.displayName = `名前は${MAX_DISPLAY_NAME_LENGTH}文字以内で入力してください`
  }
  const height = form.heightCm.trim()
  if (height !== '') {
    const value = Number(height)
    if (!Number.isInteger(value) || value < MIN_HEIGHT_CM || value > MAX_HEIGHT_CM) {
      errors.heightCm = `身長は${MIN_HEIGHT_CM}〜${MAX_HEIGHT_CM}cmの範囲で入力してください`
    }
  }
  return errors
}

/** 送信する値に変換する（validateProfile で誤りがないことを確認してから呼ぶ） */
export function toHeightCm(value: string): number | null {
  const trimmed = value.trim()
  return trimmed === '' ? null : Number(trimmed)
}
