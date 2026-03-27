import { Link, useNavigate } from 'react-router-dom'
import { LogOut, Moon, Settings, Sparkles, Sun } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { useThemeStore } from '@/store/themeStore'
import { authApi } from '@/api/auth'
import { Button } from '@/components/ui/button'

export function Topbar() {
  const { displayName, clearAuth } = useAuthStore()
  const { theme, toggleTheme } = useThemeStore()
  const navigate = useNavigate()

  const handleLogout = async () => {
    try {
      await authApi.logout('')
    } finally {
      clearAuth()
      navigate('/login')
    }
  }

  return (
    <header className="sticky top-0 z-40 border-b border-[var(--color-fate-border)] bg-[var(--color-fate-bg)]/80 backdrop-blur-md">
      <div className="container mx-auto px-4 max-w-5xl h-16 flex items-center justify-between">

        {/* Logo */}
        <Link to="/" className="flex items-center gap-2 group">
          <div className="w-8 h-8 rounded-lg bg-[var(--color-fate-gold)]/20 border border-[var(--color-fate-gold)]/40 flex items-center justify-center group-hover:glow-gold transition-all">
            <Sparkles className="w-4 h-4 text-[var(--color-fate-gold)]" />
          </div>
          <span className="font-semibold text-[var(--color-fate-text)] hidden sm:block">
            The Hand of Fate
          </span>
        </Link>

        {/* Right side */}
        <div className="flex items-center gap-2">
          <span className="text-sm text-[var(--color-fate-muted)] hidden sm:block">
            {displayName}
          </span>
          <Button variant="ghost" size="icon" onClick={toggleTheme} aria-label="Toggle theme">
            {theme === 'dark'
              ? <Sun className="w-4 h-4" />
              : <Moon className="w-4 h-4" />}
          </Button>
          <Button variant="ghost" size="icon" asChild>
            <Link to="/settings">
              <Settings className="w-4 h-4" />
            </Link>
          </Button>
          <Button variant="ghost" size="icon" onClick={handleLogout}>
            <LogOut className="w-4 h-4" />
          </Button>
        </div>
      </div>
    </header>
  )
}
