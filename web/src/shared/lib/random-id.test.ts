import { afterEach, describe, expect, it, vi } from 'vitest'
import { randomId } from './random-id'

const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/
const originalRandomUUID = Object.getOwnPropertyDescriptor(crypto, 'randomUUID')

describe('randomId', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    if (originalRandomUUID) Object.defineProperty(crypto, 'randomUUID', originalRandomUUID)
  })

  it('usa crypto.randomUUID quando disponível', () => {
    vi.spyOn(crypto, 'randomUUID').mockReturnValue('11111111-2222-4333-8444-555555555555')
    expect(randomId()).toBe('11111111-2222-4333-8444-555555555555')
  })

  it('gera um UUID v4 com getRandomValues quando randomUUID não existe (contexto inseguro, HTTP)', () => {
    Object.defineProperty(crypto, 'randomUUID', { value: undefined, configurable: true })
    const id = randomId()
    expect(id).toMatch(UUID_V4)
    expect(randomId()).not.toBe(id)
  })
})
