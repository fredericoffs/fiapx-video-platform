import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithQueryClient } from '@/test/render'
import { useSessionStore } from '@/shared/lib/session-store'
import { ChangePasswordForm } from './change-password-form'

const navigateMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
}))

interface PutResult {
  data: unknown
  response: { ok: boolean; status: number }
}

const putMock = vi.fn<(...args: unknown[]) => Promise<PutResult>>()

vi.mock('@/shared/api/client', () => ({
  apiClient: {
    PUT: (...args: unknown[]) => putMock(...args),
  },
}))

afterEach(() => {
  useSessionStore.setState({ session: null })
  putMock.mockReset()
  navigateMock.mockClear()
})

describe('ChangePasswordForm', () => {
  it('mostra erros de validação ao submeter vazio', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<ChangePasswordForm />)

    await user.click(screen.getByRole('button', { name: /trocar senha/i }))

    expect(await screen.findByText('Informe a senha atual')).toBeInTheDocument()
    expect(
      await screen.findByText('A nova senha precisa ter no mínimo 8 caracteres'),
    ).toBeInTheDocument()
  })

  it('mostra erro quando a confirmação não coincide com a nova senha', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<ChangePasswordForm />)

    await user.type(screen.getByLabelText(/senha atual/i), 'Admin@123')
    await user.type(screen.getByLabelText(/^nova senha$/i), 'nova-senha-secreta-123')
    await user.type(screen.getByLabelText(/confirmar nova senha/i), 'outra-coisa')
    await user.click(screen.getByRole('button', { name: /trocar senha/i }))

    expect(await screen.findByText('As senhas não coincidem')).toBeInTheDocument()
  })

  // Item 14: a troca de senha revoga o token antigo — a sessão precisa ficar com o token
  // novo devolvido pela API, não continuar com o que autenticou a própria requisição.
  it('atualiza a sessão com o token novo devolvido pela API ao trocar a senha com sucesso', async () => {
    useSessionStore.setState({
      session: {
        token: 'token-velho',
        email: 'user@example.com',
        role: 'USER',
        mustChangePassword: true,
      },
    })
    putMock.mockResolvedValue({
      data: {
        accessToken: 'token-novo',
        tokenType: 'Bearer',
        expiresIn: 1800,
        role: 'USER',
        mustChangePassword: false,
      },
      response: { ok: true, status: 200 },
    })
    const user = userEvent.setup()

    renderWithQueryClient(<ChangePasswordForm />)
    await user.type(screen.getByLabelText(/senha atual/i), 'senha-atual-123')
    await user.type(screen.getByLabelText(/^nova senha$/i), 'nova-senha-secreta-123')
    await user.type(screen.getByLabelText(/confirmar nova senha/i), 'nova-senha-secreta-123')
    await user.click(screen.getByRole('button', { name: /trocar senha/i }))

    await vi.waitFor(() => {
      expect(useSessionStore.getState().session?.token).toBe('token-novo')
    })
    expect(useSessionStore.getState().session?.mustChangePassword).toBe(false)
    expect(useSessionStore.getState().session?.email).toBe('user@example.com')
    expect(navigateMock).toHaveBeenCalledWith({ to: '/' })
  })

  it('mostra o aviso de troca obrigatória quando a sessão exige', () => {
    useSessionStore.setState({
      session: { token: 'x', email: 'admin@fiapx.local', role: 'ADMIN', mustChangePassword: true },
    })
    renderWithQueryClient(<ChangePasswordForm />)

    expect(
      screen.getByText('Por segurança, você precisa trocar a senha antes de continuar.'),
    ).toBeInTheDocument()
  })
})
