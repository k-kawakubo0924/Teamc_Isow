import { useEffect, useRef, useState } from 'react'
import { blockUser, unblockUser } from '../../api/blocks'
import type { ProfileResponse } from '../../api/profile'
import { useAuth } from '../../auth/authContext'
import { BlockConfirmDialog } from '../../components/block/BlockConfirmDialog'
import { blockConfirmText, unblockConfirmText } from '../../components/block/blockText'
import { ReportDialog } from '../../components/report/ReportDialog'

/**
 * 相手のプロフィールの右上の⋮（docs/profile.md「ブロック」・docs/admin.md「通報」）。
 * profile.md の当初の設計では「通報・非表示・ブロック」を並べる想定だったが、非表示（ミュート）は作っていないため、
 * 「通報」と「ブロック」を置く
 */
export function ProfileBlockMenu({ profile, onBlocked }: { profile: ProfileResponse; onBlocked: () => void }) {
  const { token } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [reporting, setReporting] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  // メニューの外を押す・Esc でメニューを閉じる
  useEffect(() => {
    if (!menuOpen) return
    const handlePointerDown = (e: PointerEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) setMenuOpen(false)
    }
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [menuOpen])

  const handleBlock = async () => {
    if (!token || busy) return
    setBusy(true)
    setError(null)
    try {
      await blockUser(profile.id, token)
      setConfirming(false)
      onBlocked()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'ブロックできませんでした。時間をおいて再度お試しください。')
    } finally {
      setBusy(false)
    }
  }

  const text = blockConfirmText(profile.username)
  return (
    <div className="profile-menu-wrap" ref={menuRef}>
      <button
        type="button"
        className="profile-header-button"
        aria-label="メニュー"
        aria-haspopup="menu"
        aria-expanded={menuOpen}
        onClick={() => setMenuOpen((open) => !open)}
      >
        <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <circle cx="12" cy="5" r="1.8" />
          <circle cx="12" cy="12" r="1.8" />
          <circle cx="12" cy="19" r="1.8" />
        </svg>
      </button>
      {menuOpen && (
        <div className="profile-menu" role="menu">
          <button
            type="button"
            role="menuitem"
            className="profile-menu-item profile-menu-danger"
            onClick={() => {
              setMenuOpen(false)
              setReporting(true)
            }}
          >
            通報
          </button>
          <button
            type="button"
            role="menuitem"
            className="profile-menu-item profile-menu-danger"
            onClick={() => {
              setMenuOpen(false)
              setError(null)
              setConfirming(true)
            }}
          >
            ブロック
          </button>
        </div>
      )}
      {reporting && (
        <ReportDialog
          targetType="USER"
          targetId={profile.id}
          owner={{ id: profile.id, username: profile.username }}
          blockable
          onClose={() => setReporting(false)}
          onBlocked={onBlocked}
        />
      )}
      {confirming && (
        <BlockConfirmDialog
          title={text.title}
          body={text.body}
          confirmLabel="ブロックする"
          busyLabel="ブロックしています..."
          busy={busy}
          error={error}
          onConfirm={handleBlock}
          onCancel={() => setConfirming(false)}
        />
      )}
    </div>
  )
}

/**
 * 自分がブロックしている相手のプロフィール（docs/profile.md「ブロック」）。
 * 投稿・フォロー数・フォロー・相談は出さず、ブロック中であることと解除のボタンだけを出す
 */
export function BlockedProfile({ profile, onUnblocked }: { profile: ProfileResponse; onUnblocked: () => void }) {
  const { token } = useAuth()
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleUnblock = async () => {
    if (!token || busy) return
    setBusy(true)
    setError(null)
    try {
      await unblockUser(profile.id, token)
      setConfirming(false)
      onUnblocked()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'ブロックを解除できませんでした。時間をおいて再度お試しください。')
      setBusy(false)
    }
  }

  const text = unblockConfirmText(profile.username)
  return (
    <section className="profile-blocked">
      <p className="profile-username">@{profile.username}</p>
      <p className="profile-blocked-title">ブロック中のユーザーです</p>
      <p className="profile-blocked-text">ブロックを解除すると、お互いの投稿・プロフィールがまた見えるようになります。</p>
      <button type="button" className="profile-blocked-button" onClick={() => setConfirming(true)}>
        ブロックを解除
      </button>
      {confirming && (
        <BlockConfirmDialog
          title={text.title}
          body={text.body}
          confirmLabel="解除する"
          busyLabel="解除しています..."
          busy={busy}
          error={error}
          onConfirm={handleUnblock}
          onCancel={() => setConfirming(false)}
        />
      )}
    </section>
  )
}
