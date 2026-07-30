import * as React from 'react'
import { cn } from '@/lib/utils'

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  ref?: React.Ref<HTMLInputElement>
}

function Input({ className, type, ref, ...props }: InputProps) {
  return (
    <input
      type={type}
      ref={ref}
      className={cn(
        'flex h-10 w-full rounded-lg border border-fate-border bg-white/5',
        'px-3 py-2 text-sm text-fate-text placeholder:text-fate-muted',
        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-fate-gold',
        'disabled:cursor-not-allowed disabled:opacity-50 transition-colors',
        className
      )}
      {...props}
    />
  )
}

export { Input }
