import { describe, expect, it } from 'vitest'
import { formatFileSize } from './format-file-size'

describe('formatFileSize', () => {
  it('retorna null para tamanho ausente', () => {
    expect(formatFileSize(null)).toBeNull()
    expect(formatFileSize(undefined)).toBeNull()
  })

  it('retorna null para valores inválidos', () => {
    expect(formatFileSize(-1)).toBeNull()
    expect(formatFileSize(Number.NaN)).toBeNull()
  })

  it('formata 0 bytes', () => {
    expect(formatFileSize(0)).toBe('0 B')
  })

  it('formata bytes sem casas decimais', () => {
    expect(formatFileSize(512)).toBe('512 B')
  })

  it('formata KB com uma casa decimal', () => {
    expect(formatFileSize(2048)).toBe('2,0 KB')
  })

  it('formata MB', () => {
    expect(formatFileSize(10_485_760)).toBe('10,0 MB')
  })

  it('formata GB', () => {
    expect(formatFileSize(1_610_612_736)).toBe('1,5 GB')
  })
})
