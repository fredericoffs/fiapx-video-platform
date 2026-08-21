import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithQueryClient } from '@/test/render'
import { LoginForm } from './login-form'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
}))

vi.mock('@/shared/api/client', () => ({
  apiClient: {
    POST: vi.fn().mockResolvedValue({ data: undefined, response: { ok: false, status: 0 } }),
  },
}))

describe('LoginForm', () => {
  it('mostra erros de validação ao submeter vazio', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<LoginForm />)

    await user.click(screen.getByRole('button', { name: /entrar/i }))

    expect(await screen.findByText('E-mail inválido')).toBeInTheDocument()
    expect(await screen.findByText('Informe a senha')).toBeInTheDocument()
  })

  it('mostra erro de e-mail para um formato inválido', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<LoginForm />)

    await user.type(screen.getByLabelText(/e-mail/i), 'nao-e-um-email')
    await user.type(screen.getByLabelText(/senha/i), 'qualquer-senha')
    await user.click(screen.getByRole('button', { name: /entrar/i }))

    expect(await screen.findByText('E-mail inválido')).toBeInTheDocument()
    expect(screen.queryByText('Informe a senha')).not.toBeInTheDocument()
  })

  it('não mostra erros quando os dados são válidos', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<LoginForm />)

    await user.type(screen.getByLabelText(/e-mail/i), 'usuario@exemplo.com')
    await user.type(screen.getByLabelText(/senha/i), 'senha-valida')
    await user.click(screen.getByRole('button', { name: /entrar/i }))

    expect(screen.queryByText('E-mail inválido')).not.toBeInTheDocument()
    expect(screen.queryByText('Informe a senha')).not.toBeInTheDocument()
  })
})
