import { useEffect, useState } from 'react'

type BackendStatus = 'checking' | 'online' | 'offline'

const POLL_INTERVAL_MS = 10_000
const TIMEOUT_MS = 3_000

async function checkHealth(): Promise<boolean> {
  try {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS)
    const res = await fetch('/actuator/health', { signal: controller.signal })
    clearTimeout(timer)
    return res.ok
  } catch {
    return false
  }
}

export function useBackendStatus(): BackendStatus {
  const [status, setStatus] = useState<BackendStatus>('checking')

  useEffect(() => {
    let cancelled = false

    const poll = async () => {
      const ok = await checkHealth()
      if (!cancelled) setStatus(ok ? 'online' : 'offline')
    }

    poll()
    const id = setInterval(poll, POLL_INTERVAL_MS)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [])

  return status
}