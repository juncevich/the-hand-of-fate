import { useBackendStatus } from '@/hooks/useBackendStatus'

const statusConfig = {
  checking: { dot: 'bg-yellow-400 animate-pulse', label: 'Backend: connecting…' },
  online:   { dot: 'bg-green-400',                label: 'Backend: online' },
  offline:  { dot: 'bg-red-500 animate-pulse',    label: 'Backend: offline' },
}

export function BackendStatusIndicator() {
  const status = useBackendStatus()
  const { dot, label } = statusConfig[status]

  return (
    <span
      role="status"
      aria-label={label}
      title={label}
      className={`pointer-events-auto block w-2.5 h-2.5 rounded-full ${dot}`}
    />
  )
}
