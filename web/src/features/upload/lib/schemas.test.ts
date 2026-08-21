import { describe, expect, it } from 'vitest'
import { ALLOWED_VIDEO_EXTENSIONS, isAllowedVideoFile, videoFileSchema } from './schemas'

function fileNamed(name: string): File {
  return new File(['conteudo'], name, { type: 'video/mp4' })
}

describe('isAllowedVideoFile', () => {
  it.each(ALLOWED_VIDEO_EXTENSIONS)('aceita a extensão .%s', (extension) => {
    expect(isAllowedVideoFile(fileNamed(`video.${extension}`))).toBe(true)
  })

  it('aceita a extensão em maiúsculas', () => {
    expect(isAllowedVideoFile(fileNamed('video.MP4'))).toBe(true)
  })

  it('rejeita uma extensão não suportada', () => {
    expect(isAllowedVideoFile(fileNamed('documento.txt'))).toBe(false)
  })

  it('rejeita um nome sem extensão', () => {
    expect(isAllowedVideoFile(fileNamed('semextensao'))).toBe(false)
  })

  it('rejeita um nome terminado em ponto', () => {
    expect(isAllowedVideoFile(fileNamed('video.'))).toBe(false)
  })
})

describe('videoFileSchema', () => {
  it('falha com mensagem amigável para formato não suportado', () => {
    const result = videoFileSchema.safeParse(fileNamed('documento.pdf'))
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0]?.message).toContain('Formato não suportado')
    }
  })

  it('passa para um formato suportado', () => {
    const result = videoFileSchema.safeParse(fileNamed('clipe.mp4'))
    expect(result.success).toBe(true)
  })
})
