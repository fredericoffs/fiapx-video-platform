import { describe, expect, it } from 'vitest'
import { friendlyProcessingError } from './friendly-processing-error'

describe('friendlyProcessingError', () => {
  it('trata ausência de mensagem', () => {
    expect(friendlyProcessingError(null)).toBe('Não foi possível processar o vídeo.')
  })

  it('reconhece duração acima do limite', () => {
    const raw = 'Vídeo com 3600s de duração excede o limite de 1800s permitido nesta infraestrutura'
    expect(friendlyProcessingError(raw)).toBe(
      'O vídeo é mais longo do que o permitido nesta infraestrutura.',
    )
  })

  it('reconhece timeout de processamento', () => {
    const raw = 'Timeout de processamento excedido após 300s para /tmp/fiapx-abc/input.mp4'
    expect(friendlyProcessingError(raw)).toBe(
      'O processamento demorou demais e foi interrompido. Tente um vídeo menor.',
    )
  })

  it('reconhece vídeo corrompido pelo erro do ffprobe', () => {
    const raw =
      'ffprobe saiu com código 1 para /tmp/fiapx-x/input.mp4: [mov,mp4,m4a,3gp,3g2,mj2 @ 0x1] moov atom not found'
    expect(friendlyProcessingError(raw)).toBe(
      'O arquivo parece corrompido ou não é um vídeo válido.',
    )
  })

  it('reconhece nenhum frame extraído', () => {
    const raw = 'Nenhum frame extraído — vídeo pode estar corrompido: /tmp/fiapx-x/input.mp4'
    expect(friendlyProcessingError(raw)).toBe(
      'O arquivo parece corrompido ou não é um vídeo válido.',
    )
  })

  it('cai no fallback genérico para erros desconhecidos', () => {
    expect(friendlyProcessingError('Falha ao criar diretório temporário para o vídeo xyz')).toBe(
      'Ocorreu um erro inesperado ao processar o vídeo.',
    )
  })
})
