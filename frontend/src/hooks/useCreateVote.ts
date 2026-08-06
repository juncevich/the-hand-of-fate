import { useMutation, useQueryClient } from '@tanstack/react-query'
import { votesApi } from '@/api/votes'
import { toast } from '@/components/ui/toaster'
import { onMutationError } from '@/lib/errors'
import type { CreateVoteRequest } from '@/types/vote'

export function useCreateVote() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (request: CreateVoteRequest) => votesApi.create(request),
    onSuccess: (_, request) => {
      queryClient.invalidateQueries({ queryKey: ['votes'] })
      toast('Голосование создано!', request.title)
    },
    onError: onMutationError,
  })
}
