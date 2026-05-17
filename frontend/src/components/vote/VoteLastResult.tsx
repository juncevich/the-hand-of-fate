import { Crown } from 'lucide-react'
import { winnerLabel } from '@/lib/utils'
import type { DrawHistoryEntry } from '@/types/vote'

interface Props {
  result: DrawHistoryEntry
}

export function VoteLastResult({ result }: Props) {
  return (
    <div className="glass p-6 mb-4 border-[var(--color-fate-gold)]/30">
      <h2 className="text-sm font-medium text-[var(--color-fate-muted)] mb-3 uppercase tracking-wider">
        Последний результат
      </h2>
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-full bg-[var(--color-fate-gold)]/20 border border-[var(--color-fate-gold)]/40 flex items-center justify-center">
          <Crown className="w-5 h-5 text-[var(--color-fate-gold)]" />
        </div>
        <div>
          <p className="font-semibold text-[var(--color-fate-text)]">{winnerLabel(result)}</p>
          {result.winnerEmail && !result.winnerOptionTitle && (
            <p className="text-xs text-[var(--color-fate-muted)]">{result.winnerEmail}</p>
          )}
        </div>
      </div>
    </div>
  )
}
