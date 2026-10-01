import { useEffect, useState } from 'react'
import { API_BASE_URL } from '../api/client'
import './HealthCheckPage.css'

type HealthResponse = {
  status: string
}

type FetchState =
  | { phase: 'loading' }
  | { phase: 'success'; data: HealthResponse }
  | { phase: 'error'; message: string }

function HealthCheckPage() {
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

export default HealthCheckPage
