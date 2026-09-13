import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithQueryClient } from '@/test/render'
import { useSessionStore } from '@/shared/lib/session-store'
import { ChangePasswordForm } from './change-password-form'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
}))

vi.mock('@/shared/api/client', () => ({
  apiClient: {
    PUT: vi.fn().mockResolvedValue({ data: undefined, response: { ok: false, status: 0 } }),
  },
}))

afterEach(() => {
  useSessionStore.setState({ session: null })
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
