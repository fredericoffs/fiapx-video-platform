import { describe, expect, it } from 'vitest'
import { pollingIntervalForPages } from './videos'

describe('orçamento de polling do histórico', () => {
  it.each([1, 2, 3, 10])('limita a seis consultas por minuto com %i páginas', (pages) => {
    expect((pages * 60000) / pollingIntervalForPages(pages)).toBe(6)
  })
})
