import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors',
  {
    variants: {
      variant: {
        default:     'bg-[var(--color-fate-gold)]/15 text-[var(--color-fate-gold)] border border-[var(--color-fate-gold)]/30',
        pending:     'bg-blue-500/15 text-blue-400 border border-blue-500/30',
        drawn:       'bg-green-500/15 text-green-400 border border-green-500/30',
        closed:      'bg-gray-500/15 text-gray-400 border border-gray-500/30',
        simple:      'bg-purple-500/15 text-purple-400 border border-purple-500/30',
        fairRotation:'bg-amber-500/15 text-amber-400 border border-amber-500/30',
      },
    },
    defaultVariants: { variant: 'default' },
  }
)

interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return <div className={cn(badgeVariants({ variant }), className)} {...props} />
}

export { Badge, badgeVariants }
