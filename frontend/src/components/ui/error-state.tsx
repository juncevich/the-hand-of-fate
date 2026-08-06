import { AlertTriangle } from 'lucide-react'

interface Props {
  message: string
  onRetry?: () => void
}

export function ErrorState({ message, onRetry }: Props) {
  return (
    <div className="flex flex-col items-center justify-center py-24 text-center">
      <div className="w-16 h-16 rounded-full bg-red-500/10 border border-red-500/20 flex items-center justify-center mb-4">
        <AlertTriangle className="w-8 h-8 text-red-400" />
      </div>
      <h3 className="text-lg font-medium text-fate-text mb-2">Не удалось загрузить данные</h3>
      <p className="text-sm text-fate-muted max-w-xs mb-4">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="text-sm text-fate-gold border border-fate-gold/30 rounded-full px-4 py-1.5 hover:bg-fate-gold/10 transition-colors"
        >
          Повторить
        </button>
      )}
    </div>
  )
}
