import type { ReactNode } from 'react'
import { ThemeToggle } from '@/app/theme-toggle'
import { LogoutButton } from '@/app/logout-button'
import { AdminLink } from '@/app/admin-link'
import { ChangePasswordLink } from '@/app/change-password-link'

export function Layout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-svh flex-col">
      <header className="flex items-center justify-between border-b px-6 py-3">
        <span className="flex items-center gap-2 font-heading text-lg font-bold tracking-tight">
          <span className="size-2.5 bg-primary" aria-hidden />
          fiapx<span className="text-primary">.</span>video
        </span>
        <div className="flex items-center gap-1">
          <ThemeToggle />
          <AdminLink />
          <ChangePasswordLink />
          <LogoutButton />
        </div>
      </header>
      <main className="flex-1 px-6 py-8">{children}</main>
    </div>
  )
}
