import { Outlet } from 'react-router-dom'
import { Topbar } from './Topbar'

export function AppShell() {
  return (
    <div className="min-h-screen flex flex-col bg-[var(--color-fate-bg)]">
      <Topbar />
      <main className="flex-1 container mx-auto px-4 py-8 max-w-5xl">
        <Outlet />
      </main>
    </div>
  )
}
