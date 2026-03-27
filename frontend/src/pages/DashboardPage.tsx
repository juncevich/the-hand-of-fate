import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { votesApi } from '@/api/votes'
import { VoteCard } from '@/components/vote/VoteCard'
import { CreateVoteDialog } from '@/components/vote/CreateVoteDialog'
import { Sparkles } from 'lucide-react'
import type { VoteSummary } from '@/types/vote'

export function DashboardPage() {
  const [page] = useState(0)

  const { data, isLoading } = useQuery({
    queryKey: ['votes', page],
    queryFn: () => votesApi.list(page),
  })

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-[var(--color-fate-text)] flex items-center gap-2">
            <Sparkles className="w-6 h-6 text-[var(--color-fate-gold)]" />
            Голосования
          </h1>
          <p className="text-sm text-[var(--color-fate-muted)] mt-1">
            Ваши активные и завершённые голосования
          </p>
        </div>
        <CreateVoteDialog />
      </div>

      {/* Votes grid */}
      {isLoading ? (
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
      <div className="w-16 h-16 rounded-full bg-[var(--color-fate-gold)]/10 border border-[var(--color-fate-gold)]/20 flex items-center justify-center mb-4">
        <Sparkles className="w-8 h-8 text-[var(--color-fate-gold)]" />
      </div>
      <h3 className="text-lg font-medium text-[var(--color-fate-text)] mb-2">
        Ещё нет голосований
      </h3>
      <p className="text-sm text-[var(--color-fate-muted)] max-w-xs">
        Создайте своё первое голосование и доверьте выбор Руке Судьбы
      </p>
    </div>
  )
}
