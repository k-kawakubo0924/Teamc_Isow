import { useEffect, useState } from 'react'
import './App.css'

// バックエンドのURLは環境変数で管理する（.env.example 参照）
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

type HealthResponse = {
  status: string
}

type FetchState =
  | { phase: 'loading' }
  | { phase: 'success'; data: HealthResponse }
  | { phase: 'error'; message: string }

function App() {
  const [state, setState] = useState<FetchState>({ phase: 'loading' })

  useEffect(() => {
    const controller = new AbortController()

    fetch(`${API_BASE_URL}/api/health`, { signal: controller.signal })
      .then((res) => {
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`)
        }
        return res.json() as Promise<HealthResponse>
      })
      .then((data) => setState({ phase: 'success', data }))
      .catch((err: unknown) => {
        if (controller.signal.aborted) return
        const message = err instanceof Error ? err.message : String(err)
        setState({ phase: 'error', message })
      })

    return () => controller.abort()
  }, [])

  return (
    <main className="health-check">
      <h1>疎通確認</h1>
      <p>
        <code>GET {API_BASE_URL}/api/health</code>
      </p>

      {state.phase === 'loading' && <p>確認中...</p>}

      {state.phase === 'success' && (
        <p className="status status-ok">
          ✅ 接続成功: <code>{JSON.stringify(state.data)}</code>
        </p>
      )}

      {state.phase === 'error' && (
        <p className="status status-error">
          ❌ 接続失敗: {state.message}
        </p>
      )}
    </main>
  )
}

export default App
