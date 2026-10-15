import { Navigate, Route, Routes } from 'react-router'
import { GuestOnly, RequireAuth } from './auth/RouteGuards'
import { TabLayout } from './components/BottomNav'
import AdminAnnouncementsPage from './pages/admin/AdminAnnouncementsPage'
import AdminContactDetailPage from './pages/admin/AdminContactDetailPage'
import AdminContactsPage from './pages/admin/AdminContactsPage'
import AdminReportDetailPage from './pages/admin/AdminReportDetailPage'
import AdminReportsPage from './pages/admin/AdminReportsPage'
import AdminLayout from './pages/admin/AdminLayout'
import AdminMastersPage from './pages/admin/AdminMastersPage'
import AdminModerationPage from './pages/admin/AdminModerationPage'
import AdminOperationLogsPage from './pages/admin/AdminOperationLogsPage'
import DmChatPage from './pages/dm/DmChatPage'
import DmListPage from './pages/dm/DmListPage'
import DmRequestsPage from './pages/dm/DmRequestsPage'
import DmSentPage from './pages/dm/DmSentPage'
import { FollowListPage } from './pages/follow/FollowListPage'
import HealthCheckPage from './pages/HealthCheckPage'
import HomePage from './pages/home/HomePage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import NotificationPage from './pages/notification/NotificationPage'
import PostDetailPage from './pages/post/PostDetailPage'
import PostEditPage from './pages/post/PostEditPage'
import PostPage from './pages/post/PostPage'
import { MyProfilePage, UserProfilePage } from './pages/profile/ProfilePage'
import SearchPage from './pages/search/SearchPage'
import SearchResultsPage from './pages/search/SearchResultsPage'
import ProfileEditPage from './pages/settings/ProfileEditPage'
import SettingsPage from './pages/settings/SettingsPage'
import BlockListPage from './pages/settings/BlockListPage'
import ContactPage from './pages/settings/ContactPage'
import SignUpPage from './pages/SignUpPage'
import SuspendedPage from './pages/SuspendedPage'

// 画面とURLの対応。ログインが必要な画面は RequireAuth の中に追加する
function App() {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        {/* 下部ナビゲーションを表示する画面（ホーム・検索・DM・プロフィールなど） */}
        <Route element={<TabLayout />}>
          <Route path="/" element={<HomePage />} />
          {/* 投稿の詳細（docs/post.md）。投稿のカードの写真・いいねの通知・投稿の直後から開く */}
          <Route path="/posts/:postId" element={<PostDetailPage />} />
          {/* 通知一覧（docs/notification.md）。ホームのベルから開く */}
          <Route path="/notifications" element={<NotificationPage />} />
          {/* 検索（docs/search.md） */}
          <Route path="/search" element={<SearchPage />} />
          <Route path="/search/results" element={<SearchResultsPage />} />
          <Route path="/profile" element={<MyProfilePage />} />
          <Route path="/users/:userId" element={<UserProfilePage />} />
          <Route path="/users/:userId/followings" element={<FollowListPage kind="followings" />} />
          <Route path="/users/:userId/followers" element={<FollowListPage kind="followers" />} />
          {/* DM（docs/dm.md） */}
          <Route path="/dm" element={<DmListPage />} />
          <Route path="/dm/requests" element={<DmRequestsPage />} />
          <Route path="/dm/sent" element={<DmSentPage />} />
        </Route>
        {/* チャット画面は画面下部に入力欄を置くため、下部ナビゲーションを表示しない */}
        <Route path="/dm/:conversationId" element={<DmChatPage />} />
        {/* 投稿作成は画面下部に「投稿」ボタンを置くため、下部ナビゲーションを表示しない */}
        <Route path="/post" element={<PostPage />} />
        {/* 投稿の編集（docs/post.md「投稿の編集」）。投稿作成と同じ画面のため、下部ナビゲーションを表示しない */}
        <Route path="/posts/:postId/edit" element={<PostEditPage />} />
        {/* 詳細設定（docs/settings.md）。見出しに戻るボタンがあるため、下部ナビゲーションを表示しない */}
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/profile" element={<ProfileEditPage />} />
        {/* ブロック中のアカウント（docs/profile.md「ブロック」）。詳細設定と同じく、下部ナビゲーションを表示しない */}
        <Route path="/settings/blocks" element={<BlockListPage />} />
        {/* お問い合わせ（docs/settings.md「お問い合わせ」）。詳細設定と同じく、下部ナビゲーションを表示しない */}
        <Route path="/settings/contact" element={<ContactPage />} />

        {/*
          管理画面（docs/admin.md）。PC 向けの別の枠で、下部ナビゲーションを表示しない。
          未ログインなら他の画面と同じくログイン画面へ移り、管理者でなければ AdminLayout が「ページが見つかりません」を出す。
          利用者アプリの画面から /admin へのリンクは置かない（管理者は URL を直接開く）
        */}
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<Navigate to="/admin/masters" replace />} />
          <Route path="masters" element={<AdminMastersPage />} />
          <Route path="announcements" element={<AdminAnnouncementsPage />} />
          <Route path="reports" element={<AdminReportsPage />} />
          <Route path="reports/:caseId" element={<AdminReportDetailPage />} />
          <Route path="contacts" element={<AdminContactsPage />} />
          <Route path="contacts/:contactId" element={<AdminContactDetailPage />} />
          <Route path="moderation" element={<AdminModerationPage />} />
          <Route path="operation-logs" element={<AdminOperationLogsPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Route>

        {/*
          存在しない URL。ログインが必要な画面と同じく、未ログインならログイン画面へ移る
          （未ログインの人に、/admin とそれ以外の存在しない URL の違いが見えないように）
        */}
        <Route path="*" element={<NotFoundPage />} />
      </Route>

      <Route element={<GuestOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<SignUpPage />} />
      </Route>

      {/*
        利用停止の画面（docs/admin.md「6. 措置」）。利用停止になるとログアウトするため、ログインの有無で振り分けない。
        利用停止の情報がなければ、画面の中でログイン画面へ移る
      */}
      <Route path="/suspended" element={<SuspendedPage />} />

      <Route path="/health" element={<HealthCheckPage />} />
    </Routes>
  )
}

export default App
