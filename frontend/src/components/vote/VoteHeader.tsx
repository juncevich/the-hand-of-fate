import { useNavigate } from 'react-router-dom'
import { ArrowLeft, Sparkles, Trash2, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import type { VoteDetail } from '@/types/vote'

interface Props {
  vote: VoteDetail
  onDraw: () => void
  onReopen: () => void
  onDelete: () => void
  isDrawLoading: boolean
  isReopenLoading: boolean
}

export function VoteHeader({ vote, onDraw, onReopen, onDelete, isDrawLoading, isReopenLoading }: Props) {
  const navigate = useNavigate()
  const canDraw =
    vote.isCreator && vote.status === 'PENDING' && (vote.options.length > 0 || vote.participants.length > 0)
  const canReopen = vote.isCreator && vote.status === 'DRAWN'

  return (
    <>
      <Button
        variant="ghost"
        onClick={() => navigate('/')}
        className="flex items-center gap-2 text-sm mb-6 px-0 hover:bg-transparent"
      >
        <ArrowLeft className="w-4 h-4" />
        Назад
      </Button>

      <div className="glass p-6 mb-4">
        <div className="flex items-start justify-between gap-4 mb-4">
          <div>
            <h1 className="text-xl font-bold text-fate-text mb-2">{vote.title}</h1>
            {vote.description && (
              <p className="text-sm text-fate-muted">{vote.description}</p>
            )}
          </div>
          {vote.isCreator && (
            <Button
              variant="ghost"
              size="icon"
              onClick={onDelete}
              className="text-fate-muted hover:text-red-400"
            >
              <Trash2 className="w-4 h-4" />
            </Button>
          )}
        </div>

        <div className="flex flex-wrap gap-2 mb-4">
          <Badge variant={vote.status.toLowerCase() as 'pending' | 'drawn' | 'closed'}>
            {vote.status === 'PENDING' ? 'Ожидает' : vote.status === 'DRAWN' ? 'Завершён' : 'Закрыт'}
          </Badge>
          <Badge variant={vote.mode === 'FAIR_ROTATION' ? 'fairRotation' : 'simple'}>
            {vote.mode === 'FAIR_ROTATION' ? 'Справедливый' : 'Простой'}
          </Badge>
          {vote.mode === 'FAIR_ROTATION' && <Badge>Раунд {vote.currentRound}</Badge>}
        </div>

        {canDraw && (
          <Button className="w-full text-base h-12 glow-gold" onClick={onDraw} isLoading={isDrawLoading}>
            <Sparkles className="w-5 h-5" />
            Пусть Рука Судьбы решит!
          </Button>
        )}
        {canReopen && (
          <Button variant="outline" className="w-full" onClick={onReopen} isLoading={isReopenLoading}>
            <RotateCcw className="w-4 h-4" />
            Голосовать снова
          </Button>
        )}
      </div>
    </>
  )
}
