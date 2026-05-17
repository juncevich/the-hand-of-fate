import { useState } from 'react'
import { UserPlus, X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import type { Participant, VoteStatus } from '@/types/vote'

interface Props {
  participants: Participant[]
  isCreator: boolean
  status: VoteStatus
  onAdd: (email: string) => void
  onRemove: (email: string) => void
  isAddLoading: boolean
}

export function VoteParticipants({ participants, isCreator, status, onAdd, onRemove, isAddLoading }: Props) {
  const [newEmail, setNewEmail] = useState('')
  const canEdit = isCreator && status === 'PENDING'

  const handleAdd = () => {
    const trimmed = newEmail.trim()
    if (!trimmed) return
    setNewEmail('')
    onAdd(trimmed)
  }

  return (
    <div className="glass p-6 mb-4">
      <h2 className="text-sm font-medium text-[var(--color-fate-muted)] mb-4 uppercase tracking-wider">
        Участники ({participants.length})
      </h2>

      <div className="space-y-2 mb-4">
        {participants.map((p) => (
          <div key={p.email} className="flex items-center gap-3 py-2">
            <div className="w-8 h-8 rounded-full bg-white/8 flex items-center justify-center text-xs font-medium text-[var(--color-fate-text)]">
              {(p.displayName ?? p.email)[0].toUpperCase()}
            </div>
            <div className="flex-1">
              {p.displayName && (
                <p className="text-sm font-medium text-[var(--color-fate-text)]">{p.displayName}</p>
              )}
              <p className="text-xs text-[var(--color-fate-muted)]">{p.email}</p>
            </div>
            {canEdit && (
              <Button
                variant="ghost"
                size="icon"
                onClick={() => onRemove(p.email)}
                className="text-[var(--color-fate-muted)] hover:text-red-400 h-6 w-6"
              >
                <X className="w-3 h-3" />
              </Button>
            )}
          </div>
        ))}
      </div>

      {canEdit && (
        <div className="flex gap-2 pt-4 border-t border-[var(--color-fate-border)]">
          <Input
            placeholder="Добавить участника по email"
            value={newEmail}
            onChange={(e) => setNewEmail(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleAdd()}
          />
          <Button variant="outline" size="icon" onClick={handleAdd} isLoading={isAddLoading}>
            <UserPlus className="w-4 h-4" />
          </Button>
        </div>
      )}
    </div>
  )
}
