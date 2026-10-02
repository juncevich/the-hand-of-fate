import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { useAuthStore } from '@/store/authStore'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Crown } from 'lucide-react'
import { format } from 'date-fns'
import { ru } from 'date-fns/locale'
import { votesApi } from '@/api/votes'
import { winnerLabel } from '@/lib/utils'

interface Props {
  voteId: string
}

export function VoteHistory({ voteId }: Props) {
  const userId = useAuthStore((s) => s.userId)
  return <HistoryPages key={`${userId}:${voteId}`} voteId={voteId} userId={userId} />
}

function HistoryPages({ voteId, userId }: Props & { userId: string | null }) {
  const [page, setPage] = useState(0)
  // Keep showing the current page while the next one loads, so the block doesn't collapse and jump.
  const { data: historyPage, isPlaceholderData } = useQuery({
    queryKey: ['vote-history', voteId, userId, page],
    queryFn: () => votesApi.getHistory(voteId, page),
    placeholderData: keepPreviousData,
  })

  const history = historyPage?.content
  if (!historyPage || !history?.length) return null

  return (
    <div className="glass p-6" aria-busy={isPlaceholderData}>
      <h2 className="text-sm font-medium text-fate-muted mb-4 uppercase tracking-wider">
        История ({historyPage.totalElements})
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
      {historyPage.totalPages > 1 && (
        <div className="mt-4 flex items-center justify-between">
          <Button variant="ghost" disabled={page === 0 || isPlaceholderData} onClick={() => setPage(page - 1)}>Назад</Button>
          <span>{page + 1} / {historyPage.totalPages}</span>
          <Button variant="ghost" disabled={page + 1 >= historyPage.totalPages || isPlaceholderData} onClick={() => setPage(page + 1)}>Далее</Button>
        </div>
      )}
    </div>
  )
}
