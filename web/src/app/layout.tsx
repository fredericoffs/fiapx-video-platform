import type { ReactNode } from 'react'
import { ThemeToggle } from '@/app/theme-toggle'

export function Layout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-svh flex-col">
      <header className="flex items-center justify-between border-b px-6 py-3">
        <span className="font-semibold">fiapx video platform</span>
        <ThemeToggle />
      </header>
      <main className="flex-1 px-6 py-8">{children}</main>
    </div>
  )
}
