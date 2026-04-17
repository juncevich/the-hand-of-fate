import * as ToastPrimitive from '@radix-ui/react-toast'
import { X } from 'lucide-react'
import { cn } from '@/lib/utils'
import { create } from 'zustand'

interface ToastState {
  toasts: Array<{ id: string; title: string; description?: string; variant?: 'default' | 'error' }>
  addToast: (t: Omit<ToastState['toasts'][number], 'id'>) => void
  removeToast: (id: string) => void
}

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  addToast: (t) =>
    set((s) => ({ toasts: [...s.toasts, { ...t, id: Math.random().toString(36).slice(2) }] })),
  removeToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}))

export function toast(title: string, description?: string, variant?: 'default' | 'error') {
  useToastStore.getState().addToast({ title, description, variant })
}

export function Toaster() {
  const { toasts, removeToast } = useToastStore()

  return (
    <ToastPrimitive.Provider swipeDirection="right">
      {toasts.map((t) => (
        <ToastPrimitive.Root
          key={t.id}
          open
          onOpenChange={(open) => !open && removeToast(t.id)}
          className={cn(
            'fixed bottom-4 right-4 z-[100] flex items-start gap-3 rounded-xl border p-4 shadow-2xl',
            'w-[360px] backdrop-blur-md',
            t.variant === 'error'
              ? 'bg-red-950/90 border-red-800/50'
              : 'bg-[var(--color-fate-surface)] border-[var(--color-fate-border)]'
          )}
        >
          <div className="flex-1">
            <ToastPrimitive.Title className="text-sm font-semibold text-[var(--color-fate-text)]">
              {t.title}
            </ToastPrimitive.Title>
            {t.description && (
              <ToastPrimitive.Description className="mt-1 text-xs text-[var(--color-fate-muted)]">
                {t.description}
              </ToastPrimitive.Description>
            )}
          </div>
          <ToastPrimitive.Close
            onClick={() => removeToast(t.id)}
            className="text-[var(--color-fate-muted)] hover:text-[var(--color-fate-text)]"
          >
            <X className="h-4 w-4" />
          </ToastPrimitive.Close>
        </ToastPrimitive.Root>
      ))}
      <ToastPrimitive.Viewport />
    </ToastPrimitive.Provider>
  )
}
