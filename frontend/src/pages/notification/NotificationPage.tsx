import { useEffect, useRef, useState, type RefObject } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { fetchAnnouncements, markAnnouncementRead, type Announcement } from '../../api/announcements'
import {
  deleteNotification,
  fetchNotifications,
  setNotificationRead,
  type NotificationItem,
} from '../../api/notifications'
import { useAuth } from '../../auth/authContext'
import { useNotificationSummary } from '../../components/notificationSummaryContext'
import { useLoadMoreOnScroll, usePagedList, type PagedListState } from '../../hooks/usePagedList'
import { timeAgo } from '../../utils/timeAgo'
import { goBack } from '../settings/goBack'
import { announcementDate, notificationLink, notificationText } from './notificationText'
import './notification.css'

type Tab = 'activity' | 'news'

/** 操作に失敗したときの通知を表示しておく時間 */
const TOAST_DURATION_MS = 4000

/**
 * 通知一覧（docs/notification.md、design/Notification.png・Nonotification.png）。
 * 「いいね・フォロー」タブにすべての通知を表示する。「お知らせ」タブには管理者が発行した運営からのお知らせを表示する。
 * タブのバッジはそれぞれの未読件数（ホームのベルには2つの合計を出す）。
 */
function NotificationPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { summary } = useNotificationSummary()
  const [tab, setTab] = useState<Tab>('activity')
  const unreadCount = summary?.unreadCount ?? 0
  const unreadAnnouncementCount = summary?.unreadAnnouncementCount ?? 0

  return (
    <main className="notification-page">
      <header className="notification-header">
        <button type="button" className="notification-back" aria-label="戻る" onClick={() => goBack(navigate, location, '/')}>
          ‹
        </button>
        <h1 className="notification-title">通知</h1>
      </header>

      <div className="notification-tabs" role="tablist" aria-label="通知の種類">
        <button
          type="button"
          role="tab"
          id="notification-tab-activity"
          aria-selected={tab === 'activity'}
          aria-controls="notification-panel"
          className={`notification-tab${tab === 'activity' ? ' notification-tab-active' : ''}`}
          onClick={() => setTab('activity')}
        >
          いいね・フォロー
          {/* メッセージの通知は DM のバッジで数えるため含めない */}
          <TabBadge count={unreadCount} />
        </button>
        <button
          type="button"
          role="tab"
          id="notification-tab-news"
          aria-selected={tab === 'news'}
          aria-controls="notification-panel"
          className={`notification-tab${tab === 'news' ? ' notification-tab-active' : ''}`}
          onClick={() => setTab('news')}
        >
          お知らせ
          <TabBadge count={unreadAnnouncementCount} />
        </button>
      </div>

      <section id="notification-panel" role="tabpanel" aria-labelledby={`notification-tab-${tab}`}>
        {tab === 'activity' ? (
          <ActivityList />
        ) : (
          <AnnouncementList />
        )}
      </section>
    </main>
  )
}

/** 「いいね・フォロー」タブ。末尾が見えてきたら次のページを読み込む */
function ActivityList() {
  const navigate = useNavigate()
  const { token } = useAuth()
  const { refresh: refreshSummary } = useNotificationSummary()
  const { state, loadMore, retry, updateItem, removeItem } = usePagedList<NotificationItem>(
    (page, authToken, signal) =>
      fetchNotifications(page, authToken, signal).then((result) => ({
        items: result.notifications,
        hasNext: result.hasNext,
      })),
    token,
  )
  const sentinelRef = useLoadMoreOnScroll(loadMore)
  const [now] = useState(() => new Date())
  const [openMenuId, setOpenMenuId] = useState<number | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    if (toast === null) return
    const timer = setTimeout(() => setToast(null), TOAST_DURATION_MS)
    return () => clearTimeout(timer)
  }, [toast])

  /** タップしたら既読にして遷移する。既読にする通信は遷移を待たずに行う */
  const handleOpen = (notification: NotificationItem) => {
    if (token && !notification.read) {
      updateItem(notification.id, { read: true })
      setNotificationRead(notification.id, true, token)
        .then(refreshSummary)
        .catch(() => {
          // 既読にできなくても遷移は続ける（次に一覧を開いたときに未読のまま表示される）
        })
    }
    navigate(notificationLink(notification))
  }

  const handleMarkUnread = async (notification: NotificationItem) => {
    setOpenMenuId(null)
    if (!token) return
    try {
      await setNotificationRead(notification.id, false, token)
      updateItem(notification.id, { read: false })
      refreshSummary()
    } catch (err) {
      setToast(err instanceof Error ? err.message : '操作できませんでした。時間をおいて再度お試しください。')
    }
  }

  const handleDelete = async (notification: NotificationItem) => {
    setOpenMenuId(null)
    if (!token) return
    try {
      await deleteNotification(notification.id, token)
      removeItem(notification.id)
      refreshSummary()
    } catch (err) {
      setToast(err instanceof Error ? err.message : '削除できませんでした。時間をおいて再度お試しください。')
    }
  }

  const notifications = state.items
  const isEmpty = notifications.length === 0 && !state.hasNext && state.status === 'idle'

  return (
    <>
      {isEmpty ? (
        <Empty title="通知はありません" hint={'いいねやフォローがあると\nここに表示されます'} />
      ) : (
        <ul className="notification-list">
          {notifications.map((notification) => (
            <li key={notification.id}>
              <NotificationCard
                notification={notification}
                now={now}
                menuOpen={openMenuId === notification.id}
                onOpen={() => handleOpen(notification)}
                onToggleMenu={() => setOpenMenuId((prev) => (prev === notification.id ? null : notification.id))}
                onCloseMenu={() => setOpenMenuId(null)}
                onMarkUnread={() => handleMarkUnread(notification)}
                onDelete={() => handleDelete(notification)}
              />
            </li>
          ))}
        </ul>
      )}

      {toast && (
        <div className="notification-toast" role="alert">
          {toast}
        </div>
      )}
      <ListFooter state={state} retry={retry} sentinelRef={sentinelRef} />
    </>
  )
}

/**
 * 「お知らせ」タブ（docs/notification.md「お知らせ」）。題名と発行日だけを出し、押すとその場で本文を開く
 * （別の画面に移ると、戻ったときにタブが「いいね・フォロー」に戻ってしまうため）。
 * 本文は一覧と一緒に読み込んであるため、開くときに通信しない。未読のものを開いたら既読にする。
 * お知らせは全員に同じものを出すため、「未読に戻す」「削除」のメニューは出さない
 */
function AnnouncementList() {
  const { token } = useAuth()
  const { refresh: refreshSummary } = useNotificationSummary()
  const { state, loadMore, retry, updateItem } = usePagedList<Announcement>(
    (page, authToken, signal) =>
      fetchAnnouncements(page, authToken, signal).then((result) => ({
        items: result.announcements,
        hasNext: result.hasNext,
      })),
    token,
  )
  const sentinelRef = useLoadMoreOnScroll(loadMore)
  const [openIds, setOpenIds] = useState<ReadonlySet<number>>(() => new Set())

  /** 開く・閉じる。未読のものを開いたら既読にする（既読にする通信は待たずに本文を開く） */
  const handleToggle = (announcement: Announcement) => {
    const opening = !openIds.has(announcement.id)
    setOpenIds((prev) => {
      const next = new Set(prev)
      if (opening) next.add(announcement.id)
      else next.delete(announcement.id)
      return next
    })
    if (opening && token && !announcement.read) {
      updateItem(announcement.id, { read: true })
      markAnnouncementRead(announcement.id, token)
        .then(refreshSummary)
        .catch(() => {
          // 既読にできなくても本文は開いたままにする（次に一覧を開いたときに未読のまま表示される）
        })
    }
  }

  const announcements = state.items
  const isEmpty = announcements.length === 0 && !state.hasNext && state.status === 'idle'

  return (
    <>
      {isEmpty ? (
        <Empty title="お知らせはありません" />
      ) : (
        <ul className="notification-list">
          {announcements.map((announcement) => {
            const open = openIds.has(announcement.id)
            const bodyId = `notification-announcement-body-${announcement.id}`
            return (
              <li key={announcement.id}>
                {/* 未読は白、既読は暗めの背景にする（通知のカードと同じ） */}
                <article
                  className={`notification-announcement${announcement.read ? ' notification-announcement-read' : ''}`}
                >
                  <button
                    type="button"
                    className="notification-announcement-toggle"
                    aria-expanded={open}
                    aria-controls={open ? bodyId : undefined}
                    onClick={() => handleToggle(announcement)}
                  >
                    <span className="notification-announcement-title">{announcement.title}</span>
                    <span className="notification-announcement-meta">
                      {!announcement.read && <span className="notification-visually-hidden">未読 </span>}
                      <time dateTime={announcement.publishedAt}>{announcementDate(announcement.publishedAt)}</time>
                      <svg
                        className="notification-announcement-chevron"
                        width="14"
                        height="14"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="1.8"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        aria-hidden="true"
                      >
                        <path d="M6 9l6 6 6-6" />
                      </svg>
                    </span>
                  </button>
                  {/* 文字のまま出す（HTML として扱わない）。改行は CSS（white-space: pre-wrap）でそのまま表示する */}
                  {open && (
                    <p id={bodyId} className="notification-announcement-body">
                      {announcement.body}
                    </p>
                  )}
                </article>
              </li>
            )
          })}
        </ul>
      )}
      <ListFooter state={state} retry={retry} sentinelRef={sentinelRef} />
    </>
  )
}

/** 一覧の下の、読み込み中・エラーの表示と、無限スクロールの目印（2つのタブで共通） */
function ListFooter<T>({
  state,
  retry,
  sentinelRef,
}: {
  state: PagedListState<T>
  retry: () => void
  sentinelRef: RefObject<HTMLDivElement | null>
}) {
  return (
    <>
      {state.status === 'loading' && (
        <p className="notification-status" role="status">
          読み込み中...
        </p>
      )}
      {state.status === 'error' && (
        <div className="notification-status" role="alert">
          <p>{state.errorMessage}</p>
          <button type="button" className="notification-retry" onClick={retry}>
            もう一度読み込む
          </button>
        </div>
      )}
      {/* 次のページがある間だけ置く、無限スクロールの目印 */}
      {state.hasNext && <div ref={sentinelRef} className="notification-sentinel" aria-hidden="true" />}
    </>
  )
}

/** タブの未読件数のバッジ。0件なら出さない */
function TabBadge({ count }: { count: number }) {
  if (count <= 0) return null
  return (
    <span className="notification-tab-badge" aria-label={`未読 ${count}件`}>
      {count > 99 ? '99+' : count}
    </span>
  )
}

/** 通知の1件（design/Notification.png）。未読は白、既読は暗めの背景にする */
function NotificationCard({
  notification,
  now,
  menuOpen,
  onOpen,
  onToggleMenu,
  onCloseMenu,
  onMarkUnread,
  onDelete,
}: {
  notification: NotificationItem
  /** 経過時間の基準（一覧を開いた時刻。DM一覧と同じ） */
  now: Date
  menuOpen: boolean
  onOpen: () => void
  onToggleMenu: () => void
  onCloseMenu: () => void
  onMarkUnread: () => void
  onDelete: () => void
}) {
  const { actor } = notification
  const menuRef = useRef<HTMLDivElement>(null)

  // メニューの外を押す・Esc でメニューを閉じる
  useEffect(() => {
    if (!menuOpen) return
    const handlePointerDown = (e: PointerEvent) => {
      if (!menuRef.current?.contains(e.target as Node)) onCloseMenu()
    }
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCloseMenu()
    }
    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [menuOpen, onCloseMenu])

  return (
    <article className={`notification-card${notification.read ? ' notification-card-read' : ''}`}>
      <Link
        to={notificationLink(notification)}
        className="notification-open"
        onClick={(e) => {
          e.preventDefault()
          onOpen()
        }}
      >
        {actor.profileImageUrl ? (
          <img className="notification-avatar" src={actor.profileImageUrl} alt="" />
        ) : (
          <span className="notification-avatar notification-avatar-empty" aria-hidden="true">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4">
              <circle cx="12" cy="8.5" r="3.5" />
              <path d="M5 20c1.1-3.4 3.7-5 7-5s5.9 1.6 7 5" strokeLinecap="round" />
            </svg>
          </span>
        )}
        <span className="notification-body">
          <span className="notification-text">
            <strong>@{actor.username}</strong> {notificationText(notification.type)}
          </span>
          <span className="notification-time">
            {!notification.read && <span className="notification-visually-hidden">未読 </span>}
            {timeAgo(notification.notifiedAt, now, { yesterday: true })}
          </span>
        </span>
      </Link>

      <div className="notification-menu-wrap" ref={menuRef}>
        <button
          type="button"
          className="notification-menu-button"
          aria-label="メニュー"
          aria-haspopup="menu"
          aria-expanded={menuOpen}
          onClick={onToggleMenu}
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <circle cx="12" cy="5" r="1.6" />
            <circle cx="12" cy="12" r="1.6" />
            <circle cx="12" cy="19" r="1.6" />
          </svg>
        </button>
        {menuOpen && (
          <div className="notification-menu" role="menu">
            {/* 未読の通知は、すでに未読のため「未読に戻す」を出さない */}
            {notification.read && (
              <button type="button" role="menuitem" className="notification-menu-item" onClick={onMarkUnread}>
                未読に戻す
              </button>
            )}
            <button
              type="button"
              role="menuitem"
              className="notification-menu-item notification-menu-danger"
              onClick={onDelete}
            >
              通知を削除
            </button>
          </div>
        )}
      </div>
    </article>
  )
}

/** 一覧が空のときの表示（design/Nonotification.png）。hint の \n で改行する */
function Empty({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="notification-empty">
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" aria-hidden="true">
        <path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2H4.5z" />
        <path d="M10 20.5a2 2 0 0 0 4 0" strokeLinecap="round" />
      </svg>
      <p className="notification-empty-title">{title}</p>
      {hint && <p className="notification-empty-hint">{hint}</p>}
    </div>
  )
}

export default NotificationPage
