import { useMutation } from '@tanstack/react-query'
import { MailCheck } from 'lucide-react'
import { authApi } from '@/api/auth'
import { Button } from '@/components/ui/button'
import { toast } from '@/components/ui/toaster'
import { extractErrorMessage } from '@/lib/errors'

/** "Check your inbox" notice with a button that mails the verification link again. */
export function ResendVerification({ email, title }: { email: string; title: string }) {
  const resend = useMutation({
    mutationFn: () => authApi.resendVerification(email),
    onSuccess: () => toast('Письмо отправлено', 'Если его нет, проверьте папку «Спам»'),
    onError: (e) => toast('Не удалось отправить письмо', extractErrorMessage(e), 'error'),
  })

  return (
    <div className="glass p-6 space-y-4 text-center" role="status">
      <MailCheck className="w-10 h-10 text-fate-gold mx-auto" />
      <p className="font-medium text-fate-text">{title}</p>
      <p className="text-sm text-fate-muted">
        Мы отправили ссылку на <span className="text-fate-text">{email}</span>. Перейдите по ней, чтобы войти.
      </p>
      <Button variant="outline" className="w-full" onClick={() => resend.mutate()} isLoading={resend.isPending}>
        Отправить письмо ещё раз
      </Button>
    </div>
  )
}
