import { useState } from 'react'
import { Plus, X } from 'lucide-react'
import { toast } from '@/components/ui/toaster'
import { useCreateVote } from '@/hooks/useCreateVote'
import { useTagList } from '@/hooks/useTagList'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger,
} from '@/components/ui/dialog'
import type { DrawTarget, VoteMode } from '@/types/vote'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function CreateVoteDialog() {
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [mode, setMode] = useState<VoteMode>('SIMPLE')
  const [drawTarget, setDrawTarget] = useState<DrawTarget>('PARTICIPANTS')
  const drawsOptions = drawTarget === 'OPTIONS'

  const optionList = useTagList()
  const emailList = useTagList({
    transform: (value) => value.trim().toLowerCase(),
    validate: (value) => EMAIL_PATTERN.test(value),
  })

  const { mutate, isPending } = useCreateVote()

  const addOption = () => optionList.add()

  const addEmail = () => {
    const value = emailList.input.trim().toLowerCase()
    if (value && !EMAIL_PATTERN.test(value)) {
      toast('Неверный email', undefined, 'error')
      return
    }
    emailList.add()
  }

  const resetAndClose = () => {
    setTitle('')
    setDescription('')
    setMode('SIMPLE')
    setDrawTarget('PARTICIPANTS')
    optionList.reset()
    emailList.reset()
    setOpen(false)
  }

  const submit = () => {
    mutate(
      {
        title,
        description: description || undefined,
        mode,
        drawTarget,
        participantEmails: emailList.items,
        // Options typed before switching back to participants are not sent: such a vote can't have them.
        options: drawsOptions ? optionList.items : [],
      },
      { onSuccess: resetAndClose },
    )
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
              maxLength={2000}
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
                      ? 'border-fate-gold bg-fate-gold/10 text-fate-gold'
                      : 'border-fate-border text-fate-muted hover:border-white/20'
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

          {/* Draw target */}
          <div className="space-y-1.5">
            <Label>Кого выбирает жеребьёвка</Label>
            <div className="grid grid-cols-2 gap-2">
              {(['PARTICIPANTS', 'OPTIONS'] as DrawTarget[]).map((t) => (
                <button
                  key={t}
                  type="button"
                  aria-pressed={drawTarget === t}
                  onClick={() => setDrawTarget(t)}
                  className={`p-3 rounded-lg border text-sm text-left transition-all ${
                    drawTarget === t
                      ? 'border-fate-gold bg-fate-gold/10 text-fate-gold'
                      : 'border-fate-border text-fate-muted hover:border-white/20'
                  }`}
                >
                  <div className="font-medium">{t === 'PARTICIPANTS' ? 'Участника' : 'Вариант'}</div>
                  <div className="text-xs mt-1 opacity-70">
                    {t === 'PARTICIPANTS'
                      ? 'Победитель — один из приглашённых'
                      : 'Победитель — один из вариантов'}
                  </div>
                </button>
              ))}
            </div>
            <p className="text-xs text-fate-muted">Изменить это после создания нельзя</p>
          </div>

          {/* Options */}
          {drawsOptions && (
            <div className="space-y-1.5">
              <Label>Варианты выбора</Label>
              <div className="flex gap-2">
                <Input
                  placeholder="Например: Пицца"
                  value={optionList.input}
                  onChange={(e) => optionList.setInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault()
                      addOption()
                    }
                  }}
                />
                <Button type="button" variant="outline" size="icon" onClick={addOption}>
                  <Plus className="w-4 h-4" />
                </Button>
              </div>
              {optionList.items.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {optionList.items.map((opt) => (
                    <span
                      key={opt}
                      className="flex items-center gap-1 bg-fate-gold/10 border border-fate-gold/30 rounded-full px-3 py-1 text-xs text-fate-gold"
                    >
                      {opt}
                      <button onClick={() => optionList.remove(opt)}>
                        <X className="w-3 h-3 hover:text-red-400" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
            </div>
          )}

          {/* Participants */}
          <div className="space-y-1.5">
            <Label>{drawsOptions ? 'Наблюдатели' : 'Участники'}</Label>
            {drawsOptions && (
              <p className="text-xs text-fate-muted">
                Получат приглашение и результат, но в жеребьёвке не участвуют
              </p>
            )}
            <div className="flex gap-2">
              <Input
                placeholder="email@example.com"
                value={emailList.input}
                onChange={(e) => emailList.setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault()
                    addEmail()
                  }
                }}
              />
              <Button type="button" variant="outline" size="icon" onClick={addEmail} aria-label="Добавить участника">
                <Plus className="w-4 h-4" />
              </Button>
            </div>
            {emailList.items.length > 0 && (
              <div className="flex flex-wrap gap-1.5 mt-2">
                {emailList.items.map((email) => (
                  <span
                    key={email}
                    className="flex items-center gap-1 bg-white/8 rounded-full px-3 py-1 text-xs text-fate-muted"
                  >
                    {email}
                    <button onClick={() => emailList.remove(email)}>
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
          <Button onClick={submit} isLoading={isPending} disabled={!title.trim()}>
            Создать
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}
