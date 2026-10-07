import { useState, type InputHTMLAttributes } from 'react'

const ICON_PROPS = {
  width: 20,
  height: 20,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 1.6,
  strokeLinecap: 'round',
  strokeLinejoin: 'round',
  'aria-hidden': true,
} as const

/**
 * 右端の目のアイコンで、入力内容の表示・非表示を切り替えられるパスワード欄。
 * 既定は伏せ字。type 以外の属性は input にそのまま渡す。
 */
export function PasswordInput(props: Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="auth-password">
      <input {...props} type={visible ? 'text' : 'password'} />
      <button
        type="button"
        className="auth-password-toggle"
        onClick={() => setVisible((prev) => !prev)}
        aria-label={visible ? 'パスワードを非表示' : 'パスワードを表示'}
        aria-pressed={visible}
        aria-controls={props.id}
      >
        {visible ? (
          <svg {...ICON_PROPS}>
            <path d="M3 3l18 18" />
            <path d="M10.6 5.1A9.8 9.8 0 0 1 12 5c6 0 9.5 7 9.5 7a16.6 16.6 0 0 1-2.9 3.8" />
            <path d="M6.6 6.6C3.9 8.4 2.5 12 2.5 12S6 19 12 19a9.4 9.4 0 0 0 5.4-1.6" />
            <path d="M9.9 9.9a3 3 0 0 0 4.2 4.2" />
          </svg>
        ) : (
          <svg {...ICON_PROPS}>
            <path d="M2.5 12S6 5 12 5s9.5 7 9.5 7-3.5 7-9.5 7-9.5-7-9.5-7z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        )}
      </button>
    </div>
  )
}
