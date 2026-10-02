/** 画面上部に出す警告文（design/Newregistrationerror.png・Loginauthenticationfailed.png） */
export function ErrorBanner({ message }: { message: string }) {
  return (
    <div className="auth-banner" role="alert">
      <span className="auth-banner-icon" aria-hidden="true">
        !
      </span>
      <p>{message}</p>
    </div>
  )
}
