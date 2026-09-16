import { afterEach, describe, expect, it, vi } from 'vitest'
import { useSessionStore } from '@/shared/lib/session-store'
import { downloadVideo } from './download-video'

function zipResponse() {
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      controller.enqueue(new Uint8Array([1, 2, 3]))
      controller.close()
    },
  })
  return new Response(body, {
    status: 200,
    headers: { 'Content-Disposition': 'attachment; filename="frames.zip"' },
  })
}

afterEach(() => {
  useSessionStore.setState({ session: null })
  delete window.showSaveFilePicker
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('downloadVideo', () => {
  // Item 15: pro maior vídeo permitido, gravar direto no disco (sem acumular o zip inteiro
  // em memória) é o caminho relevante — cobre que ele realmente é usado quando disponível,
  // e que os bytes da resposta chegam até o WritableStream.
  it('grava via File System Access API quando o navegador suporta (Chromium)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(zipResponse()))
    const written: Uint8Array[] = []
    const writable = new WritableStream<Uint8Array>({
      write(chunk) {
        written.push(chunk)
      },
    })
    const createWritable = vi.fn().mockResolvedValue(writable)
    const handle: SaveFileHandle = { createWritable }
    const showSaveFilePicker = vi.fn().mockResolvedValue(handle)
    window.showSaveFilePicker = showSaveFilePicker

    await downloadVideo('video-1', 'movie.mp4')

    expect(showSaveFilePicker).toHaveBeenCalledWith({ suggestedName: 'frames.zip' })
    expect(createWritable).toHaveBeenCalled()
    expect(written).toHaveLength(1)
  })

  it('não baixa nada quando o usuário cancela o diálogo salvar como', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(zipResponse()))
    window.showSaveFilePicker = vi
      .fn()
      .mockRejectedValue(new DOMException('cancelado', 'AbortError'))
    const createElementSpy = vi.spyOn(document, 'createElement')

    await downloadVideo('video-1', 'movie.mp4')

    expect(createElementSpy).not.toHaveBeenCalledWith('a')
  })

  it('cai pro download via Blob quando o navegador não suporta a File System Access API', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(zipResponse()))
    const anchor = document.createElement('a')
    const clickSpy = vi.spyOn(anchor, 'click').mockReturnValue(undefined)
    vi.spyOn(document, 'createElement').mockReturnValue(anchor)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:fake')
    vi.spyOn(URL, 'revokeObjectURL').mockReturnValue(undefined)

    await downloadVideo('video-1', 'movie.mp4')

    expect(clickSpy).toHaveBeenCalled()
    expect(anchor.download).toBe('frames.zip')
  })

  it('lança erro quando a resposta não é ok', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 500 })))

    await expect(downloadVideo('video-1', 'movie.mp4')).rejects.toThrow(
      'Não foi possível baixar o vídeo',
    )
  })
})
