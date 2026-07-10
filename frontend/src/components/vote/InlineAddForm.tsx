import { useState, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'

interface Props {
  placeholder: string
  onAdd: (value: string) => void
  isLoading: boolean
  icon: ReactNode
}

export function InlineAddForm({ placeholder, onAdd, isLoading, icon }: Props) {
  const [value, setValue] = useState('')

  const handleAdd = () => {
    const trimmed = value.trim()
    if (!trimmed) return
    setValue('')
    onAdd(trimmed)
  }

  return (
    <div className="flex gap-2 pt-4 border-t border-[var(--color-fate-border)]">
      <Input
        placeholder={placeholder}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleAdd())}
      />
      <Button variant="outline" size="icon" onClick={handleAdd} isLoading={isLoading} disabled={!value.trim()}>
        {icon}
      </Button>
    </div>
  )
}
