import { Route, Routes } from 'react-router'
import { GuestOnly, RequireAuth } from './auth/RouteGuards'
import HealthCheckPage from './pages/HealthCheckPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import SignUpPage from './pages/SignUpPage'

// 画面とURLの対応。ログインが必要な画面は RequireAuth の中に追加する
function App() {
  return (
    <Routes>
      <Route element={<RequireAuth />}>
        <Route path="/" element={<HomePage />} />
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
