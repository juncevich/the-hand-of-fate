import { useMutation } from '@tanstack/react-query'
import { MAX_AVATAR_BYTES, usersApi } from '@/api/users'
import { useAuthStore } from '@/store/authStore'
import { toast } from '@/components/ui/toaster'
import { onMutationError } from '@/lib/errors'

export function useAvatar() {
  const setAvatarUrl = useAuthStore((s) => s.setAvatarUrl)

  const upload = useMutation({
    mutationFn: usersApi.uploadAvatar,
    onSuccess: ({ avatarUrl }) => {
      setAvatarUrl(avatarUrl)
      toast('Аватар обновлён')
    },
    onError: onMutationError,
  })

  const remove = useMutation({
    mutationFn: usersApi.deleteAvatar,
    onSuccess: () => setAvatarUrl(null),
    onError: onMutationError,
  })

  const selectFile = (file: File) => {
    if (file.size > MAX_AVATAR_BYTES) {
      toast('Ошибка', 'Файл больше 2 МБ', 'error')
      return
    }
    upload.mutate(file)
  }

  return { upload, remove, selectFile }
}
