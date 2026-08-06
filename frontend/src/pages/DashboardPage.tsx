import { useState } from 'react'
import { useVoteList } from '@/hooks/useVoteList'
import { VoteCard } from '@/components/vote/VoteCard'
import { CreateVoteDialog } from '@/components/vote/CreateVoteDialog'
import { ErrorState } from '@/components/ui/error-state'
import { extractErrorMessage } from '@/lib/errors'
import { Sparkles } from 'lucide-react'
import type { VoteSummary } from '@/types/vote'

export function DashboardPage() {
  const [page] = useState(0)
  const { data, isLoading, isError, error, refetch } = useVoteList(page)

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-fate-text flex items-center gap-2">
            <Sparkles className="w-6 h-6 text-fate-gold" />
            Голосования
          </h1>
          <p className="text-sm text-fate-muted mt-1">
            Ваши активные и завершённые голосования
          </p>
        </div>
        <CreateVoteDialog />
      </div>

      {isError ? (
        <ErrorState message={extractErrorMessage(error)} onRetry={() => refetch()} />
      ) : isLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="glass p-5 animate-pulse h-32" />
          ))}
        </div>
      ) : data?.content?.length === 0 ? (
        <EmptyState />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {data?.content?.map((vote: VoteSummary) => (
            <VoteCard key={vote.id} vote={vote} />
          ))}
        </div>
      )}
    </div>
  )
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center justify-center py-24 text-center">
      <div className="w-16 h-16 rounded-full bg-fate-gold/10 border border-fate-gold/20 flex items-center justify-center mb-4">
        <Sparkles className="w-8 h-8 text-fate-gold" />
      </div>
      <h3 className="text-lg font-medium text-fate-text mb-2">Ещё нет голосований</h3>
      <p className="text-sm text-fate-muted max-w-xs">
        Создайте своё первое голосование и доверьте выбор Руке Судьбы
      </p>
    </div>
  )
}
