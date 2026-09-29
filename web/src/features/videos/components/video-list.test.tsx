import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { DEFAULT_VIDEO_LIST_FILTERS } from '@/shared/api/videos'
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
    data: { items, page, size: 10, totalElements },
    response: { ok: true, status: 200, headers: new Headers() },
  }
}

// Mesmo papel da DashboardPage: dona do estado de página e filtros.
function Harness() {
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState(DEFAULT_VIDEO_LIST_FILTERS)
  return (
    <VideoList
      page={page}
      filters={filters}
      onPageChange={setPage}
      onFiltersChange={(newFilters) => {
        setFilters(newFilters)
        setPage(0)
      }}
    />
  )
}

function lastQuery() {
  return (getMock.mock.lastCall?.[1] as { params: { query: Record<string, unknown> } }).params.query
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
  it('pagina de 10 em 10 e busca a próxima página ao avançar', async () => {
    loginAsUser()
    const firstPageItems = Array.from({ length: 10 }, (_, i) => videoStub(`v${String(i)}`))
    getMock
      .mockResolvedValueOnce(pageResponse(firstPageItems, 0, 11))
      .mockResolvedValueOnce(pageResponse([videoStub('v10')], 1, 11))

    renderWithQueryClient(<Harness />)

    expect(await screen.findByText('v0.mp4')).toBeInTheDocument()
    expect(screen.getByText('Página 1 de 2')).toBeInTheDocument()
    expect(lastQuery()).toMatchObject({
      page: 0,
      size: 10,
      sortBy: 'CREATED_AT',
      direction: 'DESC',
    })

    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /próxima página/i }))

    expect(await screen.findByText('v10.mp4')).toBeInTheDocument()
    expect(screen.queryByText('v0.mp4')).not.toBeInTheDocument()
    expect(screen.getByText('Página 2 de 2')).toBeInTheDocument()
    expect(lastQuery()).toMatchObject({ page: 1, size: 10 })
  })

  it('manda ordenação e período pra API e volta pra primeira página', async () => {
    loginAsUser()
    getMock.mockResolvedValue(pageResponse([videoStub('only-one')], 0, 1))

    renderWithQueryClient(<Harness />)
    expect(await screen.findByText('only-one.mp4')).toBeInTheDocument()

    const user = userEvent.setup()
    await user.selectOptions(screen.getByLabelText('Ordenar por'), 'Maior tamanho')
    await waitFor(() => {
      expect(lastQuery()).toMatchObject({ page: 0, sortBy: 'FILE_SIZE', direction: 'DESC' })
    })

    fireEvent.change(screen.getByLabelText('Enviado de'), { target: { value: '2026-09-29T10:00' } })
    await waitFor(() => {
      expect(lastQuery()).toMatchObject({
        createdFrom: new Date('2026-09-29T10:00').toISOString(),
      })
    })

    await user.click(screen.getByRole('button', { name: /limpar/i }))
    await waitFor(() => {
      expect(lastQuery()).toEqual({ page: 0, size: 10, sortBy: 'CREATED_AT', direction: 'DESC' })
    })
  })

  it('mantém os filtros visíveis quando o período não tem vídeos', async () => {
    loginAsUser()
    getMock
      .mockResolvedValueOnce(pageResponse([videoStub('only-one')], 0, 1))
      .mockResolvedValue(pageResponse([], 0, 0))

    renderWithQueryClient(<Harness />)
    expect(await screen.findByText('only-one.mp4')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Até'), { target: { value: '2020-01-01T00:00' } })

    expect(
      await screen.findByText('Nenhum vídeo encontrado com esses filtros.'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Até')).toBeInTheDocument()
  })

  it('mostra estado vazio quando o usuário não tem vídeos', async () => {
    loginAsUser()
    getMock.mockResolvedValueOnce(pageResponse([], 0, 0))

    renderWithQueryClient(<Harness />)

    expect(await screen.findByText('Nenhum vídeo enviado ainda.')).toBeInTheDocument()
  })

  it('mostra mensagem de limite de requisições quando o gateway responde 429', async () => {
    loginAsUser()
    const headers = new Headers({ 'Retry-After': '30' })
    getMock.mockResolvedValueOnce({
      data: undefined,
      response: { ok: false, status: 429, headers },
    })

    renderWithQueryClient(<Harness />)

    await waitFor(() => {
      expect(screen.getByText(/muitas requisições/i)).toBeInTheDocument()
    })
  })
})
