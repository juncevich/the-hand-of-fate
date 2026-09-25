import { useQuery } from '@tanstack/react-query'

type BackendStatus = 'checking' | 'online' | 'offline'

const POLL_INTERVAL_MS = 10_000
const TIMEOUT_MS = 3_000

async function checkHealth({ signal }: { signal: AbortSignal }): Promise<boolean> {
  // A plain timer rather than AbortSignal.timeout(), which runs on a native clock
  // that fake timers can't drive in tests.
  const timeout = new AbortController()
  const timer = setTimeout(() => timeout.abort(), TIMEOUT_MS)
  try {
    // Abort on unmount (React Query's signal) or after TIMEOUT_MS, whichever comes first
    const res = await fetch('/actuator/health', { signal: AbortSignal.any([signal, timeout.signal]) })
    return res.ok
  } catch {
    return false
  } finally {
    clearTimeout(timer)
  }
}

export function useBackendStatus(): BackendStatus {
  const { data } = useQuery({
    queryKey: ['backend-status'],
    queryFn: checkHealth,
    refetchInterval: POLL_INTERVAL_MS,
    retry: false,
  })

  if (data === undefined) return 'checking'
  return data ? 'online' : 'offline'
}
