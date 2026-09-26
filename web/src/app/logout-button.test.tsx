import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import userEvent from '@testing-library/user-event'
import { useSessionStore } from '@/shared/lib/session-store'
import { renderWithQueryClient } from '@/test/render'
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
    renderWithQueryClient(<LogoutButton />)
    expect(screen.queryByRole('button', { name: /sair/i })).not.toBeInTheDocument()
  })

  it('limpa a sessão e navega para /login ao clicar', async () => {
    useSessionStore.setState({
      session: {
        token: 'fake-token',
        email: 'user@example.com',
        role: 'USER',
        mustChangePassword: false,
      },
    })
    const user = userEvent.setup()

    renderWithQueryClient(<LogoutButton />)
    await user.click(screen.getByRole('button', { name: /sair/i }))

    expect(useSessionStore.getState().session).toBeNull()
    expect(navigateMock).toHaveBeenCalledWith({ to: '/login' })
  })

  it('limpa o cache do TanStack Query ao clicar, pra não vazar dados pra próxima sessão', async () => {
    useSessionStore.setState({
      session: {
        token: 'fake-token',
        email: 'user-a@example.com',
        role: 'USER',
        mustChangePassword: false,
      },
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    queryClient.setQueryData(['videos', 'list', 'user-a@example.com'], [{ id: 'v1' }])
    const user = userEvent.setup()

    render(
      <QueryClientProvider client={queryClient}>
        <LogoutButton />
      </QueryClientProvider>,
    )
    await user.click(screen.getByRole('button', { name: /sair/i }))

    expect(queryClient.getQueryData(['videos', 'list', 'user-a@example.com'])).toBeUndefined()
  })
})
