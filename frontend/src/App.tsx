import { Route, Routes } from 'react-router'
import HealthCheckPage from './pages/HealthCheckPage'
import LoginPage from './pages/LoginPage'
import SignUpPage from './pages/SignUpPage'

// 画面とURLの対応。未ログイン時のリダイレクトなどはステップ7で追加する
function App() {
  return (
    <Routes>
      {/* ホーム画面ができるまでは疎通確認画面を置く */}
      <Route path="/" element={<HealthCheckPage />} />
      <Route path="/register" element={<SignUpPage />} />
      <Route path="/login" element={<LoginPage />} />
    </Routes>
  )
}

export default App
