import { Crown } from 'lucide-react'
import { winnerLabel } from '@/lib/utils'
import type { DrawHistoryEntry } from '@/types/vote'

interface Props {
  result: DrawHistoryEntry
}

export function VoteLastResult({ result }: Props) {
  return (
    <div className="glass p-6 mb-4 border-fate-gold/30">
      <h2 className="text-sm font-medium text-fate-muted mb-3 uppercase tracking-wider">
        Последний результат
      </h2>
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-fate-gold/20 border border-fate-gold/40 flex items-center justify-center">
          <Crown className="w-5 h-5 text-fate-gold" />
        </div>
        <div>
          <p className="font-semibold text-fate-text">{winnerLabel(result)}</p>
          {result.winnerEmail && !result.winnerOptionTitle && (
            <p className="text-xs text-fate-muted">{result.winnerEmail}</p>
          )}
        </div>
      </div>
    </div>
  )
}
