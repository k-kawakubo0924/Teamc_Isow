import logo from '../assets/logo.png'

/**
 * ログインしていなくても見える画面（ログイン・新規登録・パスワードの再設定など）のタイトル。
 * 文字ではなくロゴの画像で出し、読み上げソフトには見出し「ISHO」として伝わるよう <h1> の中に置く。
 * ログイン後のヘッダーのタイトルは文字のまま（ここは使わない）
 */
export function AuthLogo({ spaced = false }: { spaced?: boolean }) {
  return (
    <h1 className={spaced ? 'auth-logo auth-logo-spaced' : 'auth-logo'}>
      <img className="auth-logo-image" src={logo} alt="ISHO" />
    </h1>
  )
}
