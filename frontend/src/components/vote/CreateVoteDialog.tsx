import { useState } from 'react'
import { Plus, X } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { votesApi } from '@/api/votes'
import { toast } from '@/components/ui/toaster'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import type { VoteMode } from '@/types/vote'

export function CreateVoteDialog() {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [mode, setMode] = useState<VoteMode>('SIMPLE')
  const [optionInput, setOptionInput] = useState('')
  const [options, setOptions] = useState<string[]>([])
  const [emailInput, setEmailInput] = useState('')
  const [emails, setEmails] = useState<string[]>([])

  const queryClient = useQueryClient()

  const { mutate, isPending } = useMutation({
    mutationFn: () =>
      votesApi.create({
        title,
        description: description || undefined,
        mode,
        participantEmails: emails,
        options,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['votes'] })
      toast('Голосование создано!', title)
      resetAndClose()
    },
    onError: (e: Error) => toast('Ошибка', e.message, 'error'),
  })

  const addOption = () => {
    const o = optionInput.trim()
    if (!o || options.includes(o)) return
    setOptions([...options, o])
    setOptionInput('')
  }

  const addEmail = () => {
    const e = emailInput.trim().toLowerCase()
    if (!e || emails.includes(e)) return
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e)) {
      toast('Неверный email', undefined, 'error')
      return
    }
    setEmails([...emails, e])
    setEmailInput('')
  }

  const resetAndClose = () => {
    setTitle('')
    setDescription('')
    setMode('SIMPLE')
    setOptions([])
    setOptionInput('')
    setEmails([])
    setEmailInput('')
    setOpen(false)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="w-4 h-4" />
          Создать голосование
        </Button>
      </DialogTrigger>

      <DialogContent className="max-w-md max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>✦ Новое голосование</DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* Title */}
          <div className="space-y-1.5">
            <Label htmlFor="vote-title">Название *</Label>
            <Input
              id="vote-title"
              placeholder="Кто дежурит на этой неделе?"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </div>

          {/* Description */}
          <div className="space-y-1.5">
            <Label htmlFor="vote-desc">Описание</Label>
            <Input
              id="vote-desc"
              placeholder="Необязательно"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
          </div>

          {/* Mode */}
          <div className="space-y-1.5">
            <Label>Режим голосования</Label>
            <div className="grid grid-cols-2 gap-2">
              {(['SIMPLE', 'FAIR_ROTATION'] as VoteMode[]).map((m) => (
                <button
                  key={m}
                  type="button"
                  onClick={() => setMode(m)}
                  className={`p-3 rounded-lg border text-sm text-left transition-all ${
                    mode === m
                      ? 'border-[var(--color-fate-gold)] bg-[var(--color-fate-gold)]/10 text-[var(--color-fate-gold)]'
                      : 'border-[var(--color-fate-border)] text-[var(--color-fate-muted)] hover:border-white/20'
                  }`}
                >
                  <div className="font-medium">{m === 'SIMPLE' ? 'Простой' : 'Справедливый'}</div>
                  <div className="text-xs mt-1 opacity-70">
                    {m === 'SIMPLE'
                      ? 'Случайный выбор без учёта истории'
                      : 'Каждый выиграет по одному разу за раунд'}
                  </div>
                </button>
              ))}
            </div>
          </div>

          {/* Options */}
          <div className="space-y-1.5">
            <Label>Варианты выбора</Label>
            <p className="text-xs text-[var(--color-fate-muted)]">
              Если варианты заданы — жеребьёвка выбирает из них, а не из участников
            </p>
            <div className="flex gap-2">
              <Input
                placeholder="Например: Пицца"
                value={optionInput}
                onChange={(e) => setOptionInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addOption())}
              />
              <Button type="button" variant="outline" size="icon" onClick={addOption}>
                <Plus className="w-4 h-4" />
              </Button>
            </div>
            {options.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mt-2">
                {options.map((opt) => (
                  <span
                    key={opt}
                    className="flex items-center gap-1 bg-[var(--color-fate-gold)]/10 border border-[var(--color-fate-gold)]/30 rounded-full px-3 py-1 text-xs text-[var(--color-fate-gold)]"
                  >
                    {opt}
                    <button onClick={() => setOptions(options.filter((o) => o !== opt))}>
                      <X className="w-3 h-3 hover:text-red-400" />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>

          {/* Participants */}
          <div className="space-y-1.5">
            <Label>Участники</Label>
            <div className="flex gap-2">
              <Input
                placeholder="email@example.com"
                value={emailInput}
                onChange={(e) => setEmailInput(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), addEmail())}
              />
              <Button type="button" variant="outline" size="icon" onClick={addEmail} aria-label="Добавить участника">
                <Plus className="w-4 h-4" />
              </Button>
            </div>
            {emails.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mt-2">
                {emails.map((email) => (
                  <span
                    key={email}
                    className="flex items-center gap-1 bg-white/8 rounded-full px-3 py-1 text-xs text-[var(--color-fate-muted)]"
                  >
                    {email}
                    <button onClick={() => setEmails(emails.filter((e) => e !== email))}>
                      <X className="w-3 h-3 hover:text-red-400" />
                    </button>
                  </span>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="flex justify-end gap-2 mt-6">
          <Button variant="outline" onClick={resetAndClose}>Отмена</Button>
          <Button onClick={() => mutate()} isLoading={isPending} disabled={!title.trim()}>
            Создать
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
