import { useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { votesApi } from '@/api/votes'
import { toast } from '@/components/ui/toaster'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Badge } from '@/components/ui/badge'
import { ArrowLeft, Crown, Sparkles, Trash2, UserPlus, RotateCcw } from 'lucide-react'
import { format } from 'date-fns'
import { ru } from 'date-fns/locale'
import type { DrawResult } from '@/types/vote'

export function VoteDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [newEmail, setNewEmail] = useState('')
  const [lastResult, setLastResult] = useState<DrawResult | null>(null)

  const { data: vote, isLoading } = useQuery({
    queryKey: ['vote', id],
    queryFn: () => votesApi.get(id!),
    enabled: !!id,
  })

  const drawMutation = useMutation({
    mutationFn: () => votesApi.draw(id!),
    onSuccess: (result) => {
      setLastResult(result)
      queryClient.invalidateQueries({ queryKey: ['vote', id] })
      toast('✦ Рука Судьбы выбрала!', `Победитель: ${result.winnerDisplayName ?? result.winnerEmail}`)
    },
    onError: (e: Error) => toast('Ошибка', e.message, 'error'),
  })

  const reopenMutation = useMutation({
    mutationFn: () => votesApi.reopen(id!),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['vote', id] }),
  })

  const addParticipantMutation = useMutation({
    mutationFn: () => votesApi.addParticipant(id!, newEmail.trim()),
    onSuccess: () => {
      setNewEmail('')
      queryClient.invalidateQueries({ queryKey: ['vote', id] })
      toast('Участник добавлен')
    },
    onError: (e: Error) => toast('Ошибка', e.message, 'error'),
  })

  const deleteMutation = useMutation({
    mutationFn: () => votesApi.delete(id!),
    onSuccess: () => navigate('/'),
  })

  if (isLoading) {
    return <div className="glass p-8 animate-pulse h-64" />
  }

  if (!vote) return null

  const canDraw = vote.isCreator && vote.status === 'PENDING' && vote.participants.length > 0
  const canReopen = vote.isCreator && vote.status === 'DRAWN'

  return (
    <div className="max-w-2xl mx-auto">
      {/* Back */}
      <button
        onClick={() => navigate('/')}
        className="flex items-center gap-2 text-sm text-[var(--color-fate-muted)] hover:text-[var(--color-fate-text)] mb-6 transition-colors"
      >
        <ArrowLeft className="w-4 h-4" />
        Назад
      </button>

      {/* Header */}
      <div className="glass p-6 mb-4">
        <div className="flex items-start justify-between gap-4 mb-4">
          <div>
            <h1 className="text-xl font-bold text-[var(--color-fate-text)] mb-2">{vote.title}</h1>
            {vote.description && (
              <p className="text-sm text-[var(--color-fate-muted)]">{vote.description}</p>
            )}
          </div>
          {vote.isCreator && (
            <button
              onClick={() => deleteMutation.mutate()}
              className="text-[var(--color-fate-muted)] hover:text-red-400 transition-colors"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          )}
        </div>

        <div className="flex flex-wrap gap-2 mb-4">
          <Badge variant={vote.status.toLowerCase() as 'pending' | 'drawn' | 'closed'}>
            {vote.status === 'PENDING' ? 'Ожидает' : vote.status === 'DRAWN' ? 'Завершён' : 'Закрыт'}
          </Badge>
          <Badge variant={vote.mode === 'FAIR_ROTATION' ? 'fairRotation' : 'simple'}>
            {vote.mode === 'FAIR_ROTATION' ? 'Справедливый' : 'Простой'}
          </Badge>
          {vote.mode === 'FAIR_ROTATION' && (
            <Badge>Раунд {vote.currentRound}</Badge>
          )}
        </div>

        {/* Draw button */}
        {canDraw && (
          <Button
            className="w-full text-base h-12 glow-gold"
            onClick={() => drawMutation.mutate()}
            isLoading={drawMutation.isPending}
          >
            <Sparkles className="w-5 h-5" />
            Пусть Рука Судьбы решит!
          </Button>
        )}
        {canReopen && (
          <Button variant="outline" className="w-full" onClick={() => reopenMutation.mutate()}>
            <RotateCcw className="w-4 h-4" />
            Голосовать снова
          </Button>
        )}
      </div>

      {/* Last result */}
      {(lastResult || vote.lastResult) && (
        <div className="glass p-6 mb-4 border-[var(--color-fate-gold)]/30">
          <h2 className="text-sm font-medium text-[var(--color-fate-muted)] mb-3 uppercase tracking-wider">
            Последний результат
          </h2>
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-[var(--color-fate-gold)]/20 border border-[var(--color-fate-gold)]/40 flex items-center justify-center">
              <Crown className="w-5 h-5 text-[var(--color-fate-gold)]" />
            </div>
            <div>
              <p className="font-semibold text-[var(--color-fate-text)]">
                {(lastResult?.winnerDisplayName ?? vote.lastResult?.winnerDisplayName) ||
                  (lastResult?.winnerEmail ?? vote.lastResult?.winnerEmail)}
              </p>
              <p className="text-xs text-[var(--color-fate-muted)]">
                {lastResult?.winnerEmail ?? vote.lastResult?.winnerEmail}
              </p>
            </div>
          </div>
        </div>
      )}

      {/* Participants */}
      <div className="glass p-6 mb-4">
        <h2 className="text-sm font-medium text-[var(--color-fate-muted)] mb-4 uppercase tracking-wider">
          Участники ({vote.participants.length})
        </h2>

        <div className="space-y-2 mb-4">
          {vote.participants.map((p) => (
            <div key={p.email} className="flex items-center gap-3 py-2">
              <div className="w-8 h-8 rounded-full bg-white/8 flex items-center justify-center text-xs font-medium text-[var(--color-fate-text)]">
                {(p.displayName ?? p.email)[0].toUpperCase()}
              </div>
              <div>
                {p.displayName && (
                  <p className="text-sm font-medium text-[var(--color-fate-text)]">{p.displayName}</p>
                )}
                <p className="text-xs text-[var(--color-fate-muted)]">{p.email}</p>
              </div>
            </div>
          ))}
        </div>

        {vote.isCreator && vote.status === 'PENDING' && (
          <div className="flex gap-2 pt-4 border-t border-[var(--color-fate-border)]">
            <Input
              placeholder="Добавить участника по email"
              value={newEmail}
              onChange={(e) => setNewEmail(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && addParticipantMutation.mutate()}
            />
            <Button
              variant="outline"
              size="icon"
              onClick={() => addParticipantMutation.mutate()}
              isLoading={addParticipantMutation.isPending}
            >
              <UserPlus className="w-4 h-4" />
            </Button>
          </div>
        )}
      </div>

      {/* History */}
      <HistorySection voteId={id!} />
    </div>
  )
}

function HistorySection({ voteId }: { voteId: string }) {
  const { data: history } = useQuery({
    queryKey: ['vote-history', voteId],
    queryFn: () => votesApi.getHistory(voteId),
  })

  if (!history?.length) return null

  return (
    <div className="glass p-6">
      <h2 className="text-sm font-medium text-[var(--color-fate-muted)] mb-4 uppercase tracking-wider">
        История ({history.length})
      </h2>
      <div className="space-y-3">
        {history.map((h) => (
          <div key={h.id} className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="w-6 h-6 rounded-full bg-[var(--color-fate-gold)]/15 border border-[var(--color-fate-gold)]/30 flex items-center justify-center">
                <Crown className="w-3 h-3 text-[var(--color-fate-gold)]" />
              </div>
              <div>
                <p className="text-sm text-[var(--color-fate-text)]">
                  {h.winnerDisplayName ?? h.winnerEmail}
                </p>
                <p className="text-xs text-[var(--color-fate-muted)]">Раунд {h.round}</p>
              </div>
            </div>
            <p className="text-xs text-[var(--color-fate-muted)]">
              {format(new Date(h.drawnAt), 'd MMM yyyy', { locale: ru })}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}
