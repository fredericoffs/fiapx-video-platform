import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithQueryClient } from '@/test/render'
import { RegisterForm } from './register-form'

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => vi.fn(),
  Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
}))

vi.mock('@/shared/api/client', () => ({
  apiClient: {
    POST: vi.fn().mockResolvedValue({ data: undefined, response: { ok: false, status: 0 } }),
  },
}))

describe('RegisterForm', () => {
  it('mostra erros de validação ao submeter vazio', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<RegisterForm />)

    await user.click(screen.getByRole('button', { name: /criar conta/i }))

    expect(await screen.findByText('E-mail inválido')).toBeInTheDocument()
    expect(
      await screen.findByText('A senha precisa ter no mínimo 8 caracteres'),
    ).toBeInTheDocument()
  })

  it('mostra erro de senha curta', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<RegisterForm />)

    await user.type(screen.getByLabelText(/e-mail/i), 'usuario@exemplo.com')
    await user.type(screen.getByLabelText(/senha/i), '1234567')
    await user.click(screen.getByRole('button', { name: /criar conta/i }))

    expect(
      await screen.findByText('A senha precisa ter no mínimo 8 caracteres'),
    ).toBeInTheDocument()
  })

  it('não mostra erros quando os dados são válidos', async () => {
    const user = userEvent.setup()
    renderWithQueryClient(<RegisterForm />)

    await user.type(screen.getByLabelText(/e-mail/i), 'usuario@exemplo.com')
    await user.type(screen.getByLabelText(/senha/i), 'senha-valida')
    await user.click(screen.getByRole('button', { name: /criar conta/i }))

    expect(screen.queryByText('E-mail inválido')).not.toBeInTheDocument()
    expect(screen.queryByText('A senha precisa ter no mínimo 8 caracteres')).not.toBeInTheDocument()
  })
})
