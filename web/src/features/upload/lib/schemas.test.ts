import { describe, expect, it } from 'vitest'
import {
  ALLOWED_VIDEO_EXTENSIONS,
  isAllowedVideoFile,
  MAX_VIDEO_FILE_SIZE_BYTES,
  videoFileSchema,
} from './schemas'

function fileNamed(name: string): File {
  return new File(['conteudo'], name, { type: 'video/mp4' })
}

function fileWithSize(name: string, size: number): File {
  const file = fileNamed(name)
  Object.defineProperty(file, 'size', { value: size })
  return file
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

  it('falha com mensagem amigável para arquivo maior que o limite', () => {
    const result = videoFileSchema.safeParse(
      fileWithSize('grande.mp4', MAX_VIDEO_FILE_SIZE_BYTES + 1),
    )
    expect(result.success).toBe(false)
    if (!result.success) {
      expect(result.error.issues[0]?.message).toContain('Arquivo maior que')
    }
  })

  it('passa para um arquivo exatamente no limite', () => {
    const result = videoFileSchema.safeParse(fileWithSize('limite.mp4', MAX_VIDEO_FILE_SIZE_BYTES))
    expect(result.success).toBe(true)
  })
})
