import { useQuery } from '@tanstack/react-query'
import { Crown } from 'lucide-react'
import { format } from 'date-fns'
import { ru } from 'date-fns/locale'
import { votesApi } from '@/api/votes'
import { winnerLabel } from '@/lib/utils'

interface Props {
  voteId: string
}

export function VoteHistory({ voteId }: Props) {
  const { data: history } = useQuery({
    queryKey: ['vote-history', voteId],
    queryFn: () => votesApi.getHistory(voteId),
  })

  if (!history?.length) return null

  return (
    <div className="glass p-6">
      <h2 className="text-sm font-medium text-fate-muted mb-4 uppercase tracking-wider">
        История ({history.length})
      </h2>
      <div className="space-y-3">
        {history.map((h) => (
          <div key={h.id} className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-6 h-6 rounded-full bg-fate-gold/15 border border-fate-gold/30 flex items-center justify-center">
                <Crown className="w-3 h-3 text-fate-gold" />
              </div>
              <div>
                <p className="text-sm text-fate-text">{winnerLabel(h)}</p>
                <p className="text-xs text-fate-muted">Раунд {h.round}</p>
              </div>
            </div>
            <p className="text-xs text-fate-muted">
              {format(new Date(h.drawnAt), 'd MMM yyyy', { locale: ru })}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}
