import { Route, Routes } from 'react-router'
import HealthCheckPage from './pages/HealthCheckPage'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import SignUpPage from './pages/SignUpPage'

// 画面とURLの対応。未ログイン時のリダイレクトなどはステップ7で追加する
function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/register" element={<SignUpPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/health" element={<HealthCheckPage />} />
    </Routes>
  )
}

export default App
