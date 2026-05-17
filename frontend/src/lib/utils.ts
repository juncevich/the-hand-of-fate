import { clsx, type ClassValue } from 'clsx'
import { twMerge } from 'tailwind-merge'

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

export function winnerLabel(entry: {
  winnerOptionTitle?: string | null
  winnerDisplayName?: string | null
  winnerEmail?: string | null
}) {
  return entry.winnerOptionTitle ?? entry.winnerDisplayName ?? entry.winnerEmail ?? '—'
}
