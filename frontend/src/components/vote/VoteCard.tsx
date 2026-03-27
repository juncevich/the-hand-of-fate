import { Link } from 'react-router-dom'
import { Users, RotateCcw, Zap, Crown } from 'lucide-react'
import { formatDistanceToNow } from 'date-fns'
import type { VoteSummary } from '@/types/vote'
import { Badge } from '@/components/ui/badge'

const statusLabels: Record<string, string> = {
  PENDING: 'Ожидает',
  DRAWN: 'Завершён',
  CLOSED: 'Закрыт',
}

const modeLabels: Record<string, string> = {
  SIMPLE: 'Простой',
  FAIR_ROTATION: 'Справедливый',
}

export function VoteCard({ vote }: { vote: VoteSummary }) {
  return (
    <Link to={`/votes/${vote.id}`} className="block group">
      <div className="glass p-5 hover:border-[var(--color-fate-gold)]/30 hover:bg-white/[0.06] transition-all duration-300 group-hover:translate-y-[-2px]">

        {/* Header row */}
        <div className="flex items-start justify-between gap-3 mb-3">
          <h3 className="font-semibold text-[var(--color-fate-text)] group-hover:text-[var(--color-fate-gold)] transition-colors line-clamp-2">
            {vote.title}
          </h3>
          {vote.isCreator && (
            <Crown className="w-4 h-4 text-[var(--color-fate-gold)] shrink-0 mt-0.5" />
          )}
        </div>

        {/* Badges */}
        <div className="flex flex-wrap gap-2 mb-4">
          <Badge variant={vote.status.toLowerCase() as 'pending' | 'drawn' | 'closed'}>
            {statusLabels[vote.status]}
          </Badge>
          <Badge variant={vote.mode === 'FAIR_ROTATION' ? 'fairRotation' : 'simple'}>
            {vote.mode === 'FAIR_ROTATION' ? (
              <><RotateCcw className="w-3 h-3 mr-1" />{modeLabels[vote.mode]}</>
            ) : (
              <><Zap className="w-3 h-3 mr-1" />{modeLabels[vote.mode]}</>
            )}
          </Badge>
          {vote.mode === 'FAIR_ROTATION' && vote.currentRound > 1 && (
            <Badge>Раунд {vote.currentRound}</Badge>
          )}
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between text-xs text-[var(--color-fate-muted)]">
          <span className="flex items-center gap-1">
            <Users className="w-3 h-3" />
            {vote.participantCount} участников
          </span>
          <span>{formatDistanceToNow(new Date(vote.createdAt), { addSuffix: true })}</span>
        </div>
      </div>
    </Link>
  )
}
