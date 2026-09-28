import { afterEach, describe, expect, it, vi } from 'vitest'
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithQueryClient } from '@/test/render'
import { UploadDropzone } from './upload-dropzone'

interface PendingUpload {
  file: File
  onProgress: (percent: number) => void
  resolve: () => void
  reject: (error: Error) => void
}

const pending: PendingUpload[] = []

vi.mock('@/features/upload/api/upload-video', () => ({
  uploadVideo: (file: File, onProgress: (percent: number) => void) =>
    new Promise((resolve, reject) => {
      pending.push({
        file,
        onProgress,
        resolve: () => {
          resolve({ id: file.name, status: 'QUEUED' })
        },
        reject,
      })
    }),
}))

const toastMock = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn() }))
vi.mock('sonner', () => ({ toast: toastMock }))

function video(name: string) {
  return new File(['conteúdo'], name, { type: 'video/mp4' })
}

afterEach(() => {
  pending.length = 0
  toastMock.success.mockReset()
  toastMock.error.mockReset()
})

describe('UploadDropzone', () => {
  it('envia todos os vídeos escolhidos em paralelo, com progresso por arquivo', async () => {
    const onUploadingChange = vi.fn()
    renderWithQueryClient(<UploadDropzone onUploadingChange={onUploadingChange} />)

    await userEvent.upload(screen.getByTestId('upload-input'), [video('a.mp4'), video('b.mov')])

    await waitFor(() => {
      expect(pending.map((upload) => upload.file.name)).toEqual(['a.mp4', 'b.mov'])
    })
    expect(onUploadingChange).toHaveBeenLastCalledWith(true)

    pending[0]?.onProgress(40)
    pending[1]?.onProgress(75)
    expect(await screen.findByText('40%')).toBeInTheDocument()
    expect(screen.getByText('75%')).toBeInTheDocument()

    pending[0]?.resolve()
    await waitFor(() => {
      expect(screen.queryByText('a.mp4')).not.toBeInTheDocument()
    })
    expect(screen.getByText('b.mov')).toBeInTheDocument()
    expect(toastMock.success).toHaveBeenCalledWith('a.mp4 enviado — processando')

    pending[1]?.reject(new Error('Limite de uploads atingido'))
    await waitFor(() => {
      expect(onUploadingChange).toHaveBeenLastCalledWith(false)
    })
    expect(toastMock.error).toHaveBeenCalledWith('b.mov: Limite de uploads atingido')
  })

  it('aceita vários vídeos arrastados e recusa só os inválidos', async () => {
    renderWithQueryClient(<UploadDropzone />)

    fireEvent.drop(screen.getByRole('button'), {
      dataTransfer: { files: [video('a.mp4'), video('notas.txt'), video('c.mkv')] },
    })

    await waitFor(() => {
      expect(pending.map((upload) => upload.file.name)).toEqual(['a.mp4', 'c.mkv'])
    })
    expect(toastMock.error).toHaveBeenCalledTimes(1)
    expect(toastMock.error.mock.calls[0]?.[0]).toMatch(/^notas\.txt: Formato não suportado/)
  })
})
