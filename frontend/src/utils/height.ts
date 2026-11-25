/**
 * 身長の表示（例：「160cm」）。身長は任意の項目のため、未設定なら null を返し、画面では身長の部分そのものを出さない。
 * API は未設定を null で返すが、項目がない応答（undefined）や数値でない値でも「undefinedcm」「nullcm」と出さないようにする
 * （投稿の詳細・投稿のカード・プロフィール・ユーザーの一覧で共通）
 */
export function formatHeight(heightCm: number | null | undefined): string | null {
  return typeof heightCm === 'number' && Number.isFinite(heightCm) ? `${heightCm}cm` : null
}
