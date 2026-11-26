import './mute.css'

/**
 * ミュート（投稿の非表示）を切り替えたことの知らせと、失敗したときのエラー。画面の下に短く出す。
 * 知らせは role="status"（読み上げで伝わる）、エラーは role="alert"
 */
export function MuteNotice({ notice, error }: { notice: string | null; error: string | null }) {
  if (notice === null && error === null) return null
  return (
    <div className="mute-notice-area">
      {notice !== null && (
        <p className="mute-notice" role="status">
          {notice}
        </p>
      )}
      {error !== null && (
        <p className="mute-notice mute-notice-error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
