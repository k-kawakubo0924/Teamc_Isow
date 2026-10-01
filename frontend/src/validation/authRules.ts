// 認証まわりの入力規則。
// バックエンドの RegisterRequest / AuthInputNormalizer と同じ規則にしている（変更時は両方を直すこと）。
// 最終的な判定はバックエンドが行う。ここでのチェックは、確認画面へ進む前に早めに知らせるためのもの。

export type SignUpForm = {
  email: string
  phoneNumber: string
  password: string
  username: string
}

export type SignUpField = keyof SignUpForm

export type FieldErrors = Partial<Record<SignUpField, string>>

export const INVALID_INPUT_MESSAGE = '入力内容に誤りがあります。赤い欄を修正してください。'

/** 前後の空白を除き、小文字にそろえる */
export function normalizeEmail(value: string): string {
  return value.trim().toLowerCase()
}

/** 前後の空白とハイフンを除く（090-1234-5678 → 09012345678） */
export function normalizePhoneNumber(value: string): string {
  return value.trim().replaceAll('-', '')
}

/** 前後の空白と、先頭の @ を除く（@yuu_style → yuu_style） */
export function normalizeUsername(value: string): string {
  const trimmed = value.trim()
  return trimmed.startsWith('@') ? trimmed.slice(1) : trimmed
}

/** バックエンドに送る形にそろえる。パスワードはそのまま */
export function normalizeSignUpForm(form: SignUpForm): SignUpForm {
  return {
    email: normalizeEmail(form.email),
    phoneNumber: normalizePhoneNumber(form.phoneNumber),
    password: form.password,
    username: normalizeUsername(form.username),
  }
}

export function validateSignUp(form: SignUpForm): FieldErrors {
  const { email, phoneNumber, password, username } = normalizeSignUpForm(form)
  const errors: FieldErrors = {}

  if (email === '') {
    errors.email = 'メールアドレスを入力してください'
  } else if (email.length > 255 || !/^[^\s@]+@[^\s@]+$/.test(email)) {
    errors.email = 'メールアドレスの形式が正しくありません'
  }

  if (phoneNumber === '') {
    errors.phoneNumber = '電話番号を入力してください'
  } else if (!/^0\d{9,10}$/.test(phoneNumber)) {
    errors.phoneNumber = '電話番号は10〜11桁の数字で入力してください'
  }

  if (password.trim() === '') {
    errors.password = 'パスワードを入力してください'
  } else if (!/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(password)) {
    errors.password = '8文字以上、英字と数字を含めてください'
  }

  if (username === '') {
    errors.username = 'ユーザー名を入力してください'
  } else if (!/^[A-Za-z0-9_]{1,30}$/.test(username)) {
    errors.username = 'ユーザー名は半角英数字と_で30文字以内で入力してください'
  }

  return errors
}

/** 確認画面用。11桁なら 3-4-4、10桁なら 2-4-4 で区切る */
export function formatPhoneNumber(value: string): string {
  const digits = normalizePhoneNumber(value)
  if (digits.length === 11) return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
  if (digits.length === 10) return `${digits.slice(0, 2)}-${digits.slice(2, 6)}-${digits.slice(6)}`
  return digits
}

export type LoginForm = {
  email: string
  password: string
}

/** ログインは未入力のみチェックする（形式の違いは、サーバーが認証失敗として返す） */
export function validateLogin(form: LoginForm): Partial<Record<keyof LoginForm, string>> {
  const errors: Partial<Record<keyof LoginForm, string>> = {}
  if (normalizeEmail(form.email) === '') errors.email = 'メールアドレスを入力してください'
  if (form.password.trim() === '') errors.password = 'パスワードを入力してください'
  return errors
}
