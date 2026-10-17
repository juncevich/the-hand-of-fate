import type { ReactNode } from 'react'
import { Moon, Sparkles, Sun } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useThemeStore } from '@/store/themeStore'

/** Frame of the signed-out pages: logo, subtitle, theme toggle and the background glow. */
export function AuthLayout({ subtitle, children }: { subtitle: string; children: ReactNode }) {
  const { theme, toggleTheme } = useThemeStore()

  return (
    <div className="min-h-screen flex items-center justify-center bg-fate-bg px-4">
      <Button
        variant="ghost"
        size="icon"
        onClick={toggleTheme}
        aria-label="Toggle theme"
        className="absolute top-4 right-4"
      >
        {theme === 'dark' ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
      </Button>

      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-96 h-96 bg-fate-gold/5 rounded-full blur-3xl" />
        <div className="absolute top-1/3 left-1/2 -translate-x-1/2 w-64 h-64 bg-purple-500/5 rounded-full blur-2xl" />
      </div>

      <div className="relative w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="w-16 h-16 rounded-2xl bg-fate-gold/15 border border-fate-gold/30 flex items-center justify-center mx-auto mb-4 glow-gold">
            <Sparkles className="w-8 h-8 text-fate-gold" />
          </div>
          <h1 className="text-2xl font-bold text-fate-text">The Hand of Fate</h1>
          <p className="text-sm text-fate-muted mt-1">{subtitle}</p>
        </div>
        {children}
      </div>
    </div>
  )
}
