import { Route, Routes } from 'react-router'
import { GuestOnly, RequireAuth } from './auth/RouteGuards'
import { TabLayout } from './components/BottomNav'
import HealthCheckPage from './pages/HealthCheckPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import PostPage from './pages/post/PostPage'
import SignUpPage from './pages/SignUpPage'

// 画面とURLの対応。ログインが必要な画面は RequireAuth の中に追加する
function App() {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        {/* 下部ナビゲーションを表示する画面（ホーム・検索・DM・プロフィールなど） */}
        <Route element={<TabLayout />}>
          <Route path="/" element={<HomePage />} />
        </Route>
        {/* 投稿作成は画面下部に「投稿」ボタンを置くため、下部ナビゲーションを表示しない */}
        <Route path="/post" element={<PostPage />} />
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
