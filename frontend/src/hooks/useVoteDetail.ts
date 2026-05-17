import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { votesApi } from '@/api/votes'
import { toast } from '@/components/ui/toaster'
import { winnerLabel } from '@/lib/utils'

export function useVoteDetail(id: string) {
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['vote', id] })

  const query = useQuery({
    queryKey: ['vote', id],
    queryFn: () => votesApi.get(id),
    enabled: !!id,
  })

  const draw = useMutation({
    mutationFn: () => votesApi.draw(id),
    onSuccess: (result) => {
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['vote-history', id] })
      toast('✦ Рука Судьбы выбрала!', `Победитель: ${winnerLabel(result)}`)
    },
    onError: (e: Error) => toast('Ошибка', e.message, 'error'),
  })

  const reopen = useMutation({
    mutationFn: () => votesApi.reopen(id),
    onSuccess: invalidate,
  })

  const addParticipant = useMutation({
    mutationFn: (email: string) => votesApi.addParticipant(id, email),
    onSuccess: () => {
      invalidate()
      toast('Участник добавлен')
    },
    onError: (e: Error) => toast('Ошибка', e.message, 'error'),
  })

  const removeParticipant = useMutation({
    mutationFn: (email: string) => votesApi.removeParticipant(id, email),
    onSuccess: () => {
      invalidate()
      toast('Участник удалён')
    },
    onError: (e: Error) => toast('Ошибка', e.message, 'error'),
  })

  const addOption = useMutation({
    mutationFn: (title: string) => votesApi.addOption(id, title),
    onSuccess: () => {
      invalidate()
      toast('Вариант добавлен')
    },
    onError: (e: Error) => toast('Ошибка', e.message, 'error'),
  })

  const removeOption = useMutation({
    mutationFn: (optionId: string) => votesApi.removeOption(id, optionId),
    onSuccess: () => {
      invalidate()
      toast('Вариант удалён')
    },
    onError: (e: Error) => toast('Ошибка', e.message, 'error'),
  })

  const deleteVote = useMutation({
    mutationFn: () => votesApi.delete(id),
    onSuccess: () => navigate('/'),
  })

  return {
    vote: query.data,
    isLoading: query.isLoading,
    draw,
    reopen,
    addParticipant,
    removeParticipant,
    addOption,
    removeOption,
    deleteVote,
  }
}
