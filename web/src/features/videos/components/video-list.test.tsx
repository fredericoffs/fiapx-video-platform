import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useSessionStore } from '@/shared/lib/session-store'
import { renderWithQueryClient } from '@/test/render'
import { VideoList } from './video-list'

interface GetResult {
  data: unknown
  response: { ok: boolean; status: number; headers: Headers }
}

const getMock = vi.fn<(...args: unknown[]) => Promise<GetResult>>()

vi.mock('@/shared/api/client', () => ({
  apiClient: {
    GET: (...args: unknown[]) => getMock(...args),
    DELETE: vi.fn(),
  },
}))

function videoStub(id: string, status: 'QUEUED' | 'COMPLETED' = 'COMPLETED') {
  return {
    id,
    userId: 'user-1',
    originalFilename: `${id}.mp4`,
    fileSizeBytes: 1024,
    status,
    errorMessage: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }
}

function pageResponse(items: ReturnType<typeof videoStub>[], page: number, totalElements: number) {
  return {
    data: { items, page, size: 20, totalElements },
    response: { ok: true, status: 200, headers: new Headers() },
  }
}

afterEach(() => {
  useSessionStore.setState({ session: null })
  getMock.mockReset()
})

function loginAsUser() {
  useSessionStore.setState({
    session: {
      token: 'fake-token',
      email: 'user@example.com',
      role: 'USER',
      mustChangePassword: false,
    },
  })
}

describe('VideoList', () => {
  it('mostra "Carregar mais" quando há mais páginas e busca a próxima ao clicar', async () => {
    loginAsUser()
    const firstPageItems = Array.from({ length: 20 }, (_, i) => videoStub(`v${String(i)}`))
    const secondPageItems = [videoStub('v20')]
    getMock
      .mockResolvedValueOnce(pageResponse(firstPageItems, 0, 21))
      .mockResolvedValueOnce(pageResponse(secondPageItems, 1, 21))

    renderWithQueryClient(<VideoList />)

    expect(await screen.findByText('v0.mp4')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /carregar mais/i })).toBeInTheDocument()
    expect(screen.queryByText('v20.mp4')).not.toBeInTheDocument()

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /carregar mais/i }))

    expect(await screen.findByText('v20.mp4')).toBeInTheDocument()
    expect(getMock).toHaveBeenCalledTimes(2)
    expect(getMock.mock.calls[1]?.[1]).toMatchObject({ params: { query: { page: 1, size: 20 } } })
  })

  it('não mostra "Carregar mais" quando todos os vídeos já foram carregados', async () => {
    loginAsUser()
    getMock.mockResolvedValueOnce(pageResponse([videoStub('only-one')], 0, 1))

    renderWithQueryClient(<VideoList />)

    expect(await screen.findByText('only-one.mp4')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /carregar mais/i })).not.toBeInTheDocument()
  })

  it('mostra estado vazio quando o usuário não tem vídeos', async () => {
    loginAsUser()
    getMock.mockResolvedValueOnce(pageResponse([], 0, 0))

    renderWithQueryClient(<VideoList />)

    expect(await screen.findByText('Nenhum vídeo enviado ainda.')).toBeInTheDocument()
  })

  it('mostra mensagem de limite de requisições quando o gateway responde 429', async () => {
    loginAsUser()
    const headers = new Headers({ 'Retry-After': '30' })
    getMock.mockResolvedValueOnce({
      data: undefined,
      response: { ok: false, status: 429, headers },
    })

    renderWithQueryClient(<VideoList />)

    await waitFor(() => {
      expect(screen.getByText(/muitas requisições/i)).toBeInTheDocument()
    })
  })
})
