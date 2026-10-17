import { ChevronLeft, ChevronRight, ListChecks, Plus, X } from 'lucide-react'
import { InlineAddForm } from '@/components/vote/InlineAddForm'
import type { VoteOption, VoteStatus } from '@/types/vote'

interface Props {
  options: VoteOption[]
  isCreator: boolean
  status: VoteStatus
  onAdd: (title: string) => void
  onRemove: (optionId: string) => void
  /** Receives every option id in the new order. */
  onReorder: (optionIds: string[]) => void
  isAddLoading: boolean
  isReorderLoading?: boolean
}

export function VoteOptions({
  options, isCreator, status, onAdd, onRemove, onReorder, isAddLoading, isReorderLoading = false,
}: Props) {
  const canEdit = isCreator && status === 'PENDING'

  const move = (index: number, offset: -1 | 1) => {
    const ids = options.map((o) => o.id)
    ;[ids[index], ids[index + offset]] = [ids[index + offset], ids[index]]
    onReorder(ids)
  }

  return (
    <div className="glass p-6 mb-4">
      <h2 className="text-sm font-medium text-fate-muted mb-4 uppercase tracking-wider flex items-center gap-2">
        <ListChecks className="w-4 h-4" />
        Варианты ({options.length})
      </h2>

      {options.length === 0 && (
        <p className="text-sm text-fate-muted mb-4">
          Добавьте хотя бы один вариант — жеребьёвка выбирает из них
        </p>
      )}

      {options.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-4">
          {options.map((opt, index) => (
            <span
              key={opt.id}
              className="flex items-center gap-1.5 bg-fate-gold/10 border border-fate-gold/30 rounded-full px-3 py-1.5 text-sm text-fate-gold"
            >
              {canEdit && options.length > 1 && (
                <button
                  onClick={() => move(index, -1)}
                  disabled={index === 0 || isReorderLoading}
                  aria-label={`Переместить «${opt.title}» раньше`}
                  className="hover:text-fate-text transition-colors disabled:opacity-30"
                >
                  <ChevronLeft className="w-3 h-3" />
                </button>
              )}
              {opt.title}
              {canEdit && options.length > 1 && (
                <button
                  onClick={() => move(index, 1)}
                  disabled={index === options.length - 1 || isReorderLoading}
                  aria-label={`Переместить «${opt.title}» позже`}
                  className="hover:text-fate-text transition-colors disabled:opacity-30"
                >
                  <ChevronRight className="w-3 h-3" />
                </button>
              )}
              {canEdit && (
                <button
                  onClick={() => onRemove(opt.id)}
                  aria-label={`Удалить «${opt.title}»`}
                  className="hover:text-red-400 transition-colors"
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </span>
          ))}
        </div>
      )}

      {canEdit && (
        <InlineAddForm
          placeholder="Добавить вариант"
          onAdd={onAdd}
          isLoading={isAddLoading}
          icon={<Plus className="w-4 h-4" />}
        />
      )}
    </div>
  )
}
