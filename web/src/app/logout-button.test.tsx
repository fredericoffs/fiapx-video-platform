import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useSessionStore } from '@/shared/lib/session-store'
import { LogoutButton } from './logout-button'

const navigateMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
}))

afterEach(() => {
  useSessionStore.setState({ session: null })
  navigateMock.mockClear()
})

describe('LogoutButton', () => {
  it('não renderiza nada sem sessão ativa', () => {
    render(<LogoutButton />)
    expect(screen.queryByRole('button', { name: /sair/i })).not.toBeInTheDocument()
  })

  it('limpa a sessão e navega para /login ao clicar', async () => {
    useSessionStore.setState({ session: { token: 'fake-token', email: 'user@example.com' } })
    const user = userEvent.setup()

    render(<LogoutButton />)
    await user.click(screen.getByRole('button', { name: /sair/i }))

    expect(useSessionStore.getState().session).toBeNull()
    expect(navigateMock).toHaveBeenCalledWith({ to: '/login' })
  })
})
