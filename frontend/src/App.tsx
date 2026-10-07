import { Route, Routes } from 'react-router'
import { GuestOnly, RequireAuth } from './auth/RouteGuards'
import { TabLayout } from './components/BottomNav'
import DmChatPage from './pages/dm/DmChatPage'
import DmListPage from './pages/dm/DmListPage'
import DmRequestsPage from './pages/dm/DmRequestsPage'
import DmSentPage from './pages/dm/DmSentPage'
import { FollowListPage } from './pages/follow/FollowListPage'
import HealthCheckPage from './pages/HealthCheckPage'
import HomePage from './pages/home/HomePage'
import LoginPage from './pages/LoginPage'
import PostPage from './pages/post/PostPage'
import { MyProfilePage, UserProfilePage } from './pages/profile/ProfilePage'
import SearchPage from './pages/search/SearchPage'
import SearchResultsPage from './pages/search/SearchResultsPage'
import ProfileEditPage from './pages/settings/ProfileEditPage'
import SettingsPage from './pages/settings/SettingsPage'
import SignUpPage from './pages/SignUpPage'

// 画面とURLの対応。ログインが必要な画面は RequireAuth の中に追加する
function App() {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        {/* 下部ナビゲーションを表示する画面（ホーム・検索・DM・プロフィールなど） */}
        <Route element={<TabLayout />}>
          <Route path="/" element={<HomePage />} />
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
        {/* 詳細設定（docs/settings.md）。見出しに戻るボタンがあるため、下部ナビゲーションを表示しない */}
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/profile" element={<ProfileEditPage />} />
      </Route>

      <Route element={<GuestOnly />}>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<SignUpPage />} />
      </Route>

      <Route path="/health" element={<HealthCheckPage />} />
    </Routes>
  )
}

export default App
