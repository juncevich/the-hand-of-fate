import { useState } from 'react'

interface Options {
  transform?: (value: string) => string
  validate?: (value: string) => boolean
}

export function useTagList({ transform, validate }: Options = {}) {
  const [input, setInput] = useState('')
  const [items, setItems] = useState<string[]>([])

  const add = () => {
    const value = transform ? transform(input) : input.trim()
    if (!value || items.includes(value)) return false
    if (validate && !validate(value)) return false
    setItems((prev) => [...prev, value])
    setInput('')
    return true
  }

  const remove = (value: string) => setItems((prev) => prev.filter((v) => v !== value))

  const reset = () => {
    setItems([])
    setInput('')
  }

  return { input, setInput, items, add, remove, reset }
}
