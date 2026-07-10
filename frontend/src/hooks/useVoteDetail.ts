import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { votesApi } from '@/api/votes'
import { toast } from '@/components/ui/toaster'
import { onMutationError } from '@/lib/errors'
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
    onError: onMutationError,
  })

  const reopen = useMutation({
    mutationFn: () => votesApi.reopen(id),
    onSuccess: invalidate,
    onError: onMutationError,
  })

  const addParticipant = useMutation({
    mutationFn: (email: string) => votesApi.addParticipant(id, email),
    onSuccess: () => {
      invalidate()
      toast('Участник добавлен')
    },
    onError: onMutationError,
  })

  const removeParticipant = useMutation({
    mutationFn: (email: string) => votesApi.removeParticipant(id, email),
    onSuccess: () => {
      invalidate()
      toast('Участник удалён')
    },
    onError: onMutationError,
  })

  const addOption = useMutation({
    mutationFn: (title: string) => votesApi.addOption(id, title),
    onSuccess: () => {
      invalidate()
      toast('Вариант добавлен')
    },
    onError: onMutationError,
  })

  const removeOption = useMutation({
    mutationFn: (optionId: string) => votesApi.removeOption(id, optionId),
    onSuccess: () => {
      invalidate()
      toast('Вариант удалён')
    },
    onError: onMutationError,
  })

  const deleteVote = useMutation({
    mutationFn: () => votesApi.delete(id),
    onSuccess: () => navigate('/'),
    onError: onMutationError,
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
