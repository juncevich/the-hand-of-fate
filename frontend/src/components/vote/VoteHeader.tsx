import { useState } from 'react'
import { useNavigate } from 'react-router'
import { ArrowLeft, Sparkles, Trash2, RotateCcw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import type { VoteDetail } from '@/types/vote'

interface Props {
  vote: VoteDetail
  onDraw: () => void
  onReopen: () => void
  onDelete: () => void
  isDrawLoading: boolean
  isReopenLoading: boolean
  isDeleteLoading: boolean
}

export function VoteHeader({
  vote, onDraw, onReopen, onDelete, isDrawLoading, isReopenLoading, isDeleteLoading,
}: Props) {
  const navigate = useNavigate()
  const [confirmDelete, setConfirmDelete] = useState(false)
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
            <Dialog open={confirmDelete} onOpenChange={setConfirmDelete}>
              <DialogTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label="Удалить голосование"
                  className="text-fate-muted hover:text-red-400"
                >
                  <Trash2 className="w-4 h-4" />
                </Button>
              </DialogTrigger>
              <DialogContent className="max-w-md">
                <DialogHeader>
                  <DialogTitle>Удалить голосование?</DialogTitle>
                  <DialogDescription>
                    «{vote.title}» и вся история его жеребьёвок будут удалены безвозвратно.
                  </DialogDescription>
                </DialogHeader>
                <div className="flex justify-end gap-2 mt-4">
                  <Button variant="outline" onClick={() => setConfirmDelete(false)}>Отмена</Button>
                  <Button
                    className="bg-red-600 hover:bg-red-500 text-white"
                    onClick={onDelete}
                    isLoading={isDeleteLoading}
                  >
                    Удалить
                  </Button>
                </div>
              </DialogContent>
            </Dialog>
          )}
        </div>

        <div className="flex flex-wrap gap-2 mb-4">
          <Badge variant={vote.status.toLowerCase() as 'pending' | 'drawn' | 'closed'}>
            {vote.status === 'PENDING' ? 'Ожидает' : vote.status === 'DRAWN' ? 'Завершён' : 'Закрыт'}
          </Badge>
          <Badge variant={vote.mode === 'FAIR_ROTATION' ? 'fairRotation' : 'simple'}>
            {vote.mode === 'FAIR_ROTATION' ? 'Справедливый' : 'Простой'}
          </Badge>
          <Badge>{vote.drawTarget === 'OPTIONS' ? 'Из вариантов' : 'Из участников'}</Badge>
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
