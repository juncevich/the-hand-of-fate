import { useQuery } from '@tanstack/react-query'
import { votesApi } from '@/api/votes'

export function useVoteList(page = 0) {
  return useQuery({
    queryKey: ['votes', page],
    queryFn: () => votesApi.list(page),
  })
}
