/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // 5173 が使われていたら、別のポート（5174 など）に移らずに起動を止める。移ると、バックエンドが許可している画面の URL
  // （FRONTEND_ORIGIN。既定は http://localhost:5173）と食い違い、CORS で API がすべて失敗するため（docs/setup.md「詰まったとき」）
  server: {
    port: 5173,
    strictPort: true,
  },
  // 画面の部品のテスト（npm test）。ブラウザの代わりに jsdom で描画する
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
