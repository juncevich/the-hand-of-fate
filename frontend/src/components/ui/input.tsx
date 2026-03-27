import * as React from 'react'
import { cn } from '@/lib/utils'

const Input = React.forwardRef<HTMLInputElement, React.InputHTMLAttributes<HTMLInputElement>>(
  ({ className, type, ...props }, ref) => (
    <input
      type={type}
      ref={ref}
      className={cn(
        'flex h-10 w-full rounded-lg border border-[var(--color-fate-border)] bg-white/5',
        'px-3 py-2 text-sm text-[var(--color-fate-text)] placeholder:text-[var(--color-fate-muted)]',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-fate-gold)]',
        'disabled:cursor-not-allowed disabled:opacity-50 transition-colors',
        className
      )}
      {...props}
    />
  )
)
Input.displayName = 'Input'

export { Input }
