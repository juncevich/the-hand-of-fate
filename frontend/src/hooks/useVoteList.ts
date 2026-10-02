import { useAuthStore } from '@/store/authStore'
import { useQuery } from '@tanstack/react-query'
import { votesApi } from '@/api/votes'

export function useVoteList(page = 0) {
  const userId = useAuthStore((s) => s.userId)
  return useQuery({
    queryKey: ['votes', page, userId],
    queryFn: () => votesApi.list(page),
  })
}
