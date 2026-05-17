import { useState } from 'react'
import { ListChecks, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
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
  const [newOption, setNewOption] = useState('')
  const canEdit = isCreator && status === 'PENDING'

  const handleAdd = () => {
    const trimmed = newOption.trim()
    if (!trimmed) return
    setNewOption('')
    onAdd(trimmed)
  }

  return (
    <div className="glass p-6 mb-4">
      <h2 className="text-sm font-medium text-[var(--color-fate-muted)] mb-4 uppercase tracking-wider flex items-center gap-2">
        <ListChecks className="w-4 h-4" />
        Варианты ({options.length})
      </h2>

      {options.length === 0 && (
        <p className="text-sm text-[var(--color-fate-muted)] mb-4">
          Нет вариантов — жеребьёвка будет выбирать из участников
        </p>
      )}

      {options.length > 0 && (
        <div className="flex flex-wrap gap-2 mb-4">
          {options.map((opt) => (
            <span
              key={opt.id}
              className="flex items-center gap-1.5 bg-[var(--color-fate-gold)]/10 border border-[var(--color-fate-gold)]/30 rounded-full px-3 py-1.5 text-sm text-[var(--color-fate-gold)]"
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
        <div className="flex gap-2 pt-4 border-t border-[var(--color-fate-border)]">
          <Input
            placeholder="Добавить вариант"
            value={newOption}
            onChange={(e) => setNewOption(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleAdd()}
          />
          <Button
            variant="outline"
            size="icon"
            onClick={handleAdd}
            isLoading={isAddLoading}
            disabled={!newOption.trim()}
          >
            <X className="w-4 h-4 rotate-45" />
          </Button>
        </div>
      )}
    </div>
  )
}
