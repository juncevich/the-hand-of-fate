import { ListChecks, Plus, X } from 'lucide-react'
import { InlineAddForm } from '@/components/vote/InlineAddForm'
import type { VoteOption, VoteStatus } from '@/types/vote'

interface Props {
  options: VoteOption[]
  isCreator: boolean
  status: VoteStatus
  onAdd: (title: string) => void
  onRemove: (optionId: string) => void
  isAddLoading: boolean
}

export function VoteOptions({ options, isCreator, status, onAdd, onRemove, isAddLoading }: Props) {
  const canEdit = isCreator && status === 'PENDING'

  return (
    <div className="glass p-6 mb-4">
      <h2 className="text-sm font-medium text-fate-muted mb-4 uppercase tracking-wider flex items-center gap-2">
        <ListChecks className="w-4 h-4" />
        Варианты ({options.length})
      </h2>

      {options.length === 0 && (
        <p className="text-sm text-fate-muted mb-4">
          Нет вариантов — жеребьёвка будет выбирать из участников
        </p>
      )}

      {options.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-4">
          {options.map((opt) => (
            <span
              key={opt.id}
              className="flex items-center gap-1.5 bg-fate-gold/10 border border-fate-gold/30 rounded-full px-3 py-1.5 text-sm text-fate-gold"
            >
              {opt.title}
              {canEdit && (
                <button onClick={() => onRemove(opt.id)} className="hover:text-red-400 transition-colors">
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
