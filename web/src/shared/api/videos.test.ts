import { describe, expect, it } from 'vitest'
import { DEFAULT_VIDEO_LIST_FILTERS, showsNewUploadsFirst, toVideoListQuery } from './videos'

describe('toVideoListQuery', () => {
  it('sem datas manda só a ordenação', () => {
    expect(toVideoListQuery(DEFAULT_VIDEO_LIST_FILTERS)).toEqual({
      sortBy: 'CREATED_AT',
      direction: 'DESC',
    })
  })

  it('converte a hora local pra ISO e faz o "até" cobrir o minuto inteiro', () => {
    const query = toVideoListQuery({
      ...DEFAULT_VIDEO_LIST_FILTERS,
      createdFrom: '2026-09-29T10:00',
      createdTo: '2026-09-29T15:00',
    })

    expect(query.createdFrom).toBe(new Date('2026-09-29T10:00').toISOString())
    expect(query.createdTo).toBe(
      new Date(new Date('2026-09-29T15:00').getTime() + 59_999).toISOString(),
    )
  })
})

describe('showsNewUploadsFirst', () => {
  it('só a primeira página em "mais recentes" sem teto de data', () => {
    expect(showsNewUploadsFirst({ page: 0, filters: DEFAULT_VIDEO_LIST_FILTERS })).toBe(true)
    expect(showsNewUploadsFirst({ page: 1, filters: DEFAULT_VIDEO_LIST_FILTERS })).toBe(false)
    expect(
      showsNewUploadsFirst({
        page: 0,
        filters: { ...DEFAULT_VIDEO_LIST_FILTERS, sortBy: 'FILENAME', direction: 'ASC' },
      }),
    ).toBe(false)
    expect(
      showsNewUploadsFirst({
        page: 0,
        filters: { ...DEFAULT_VIDEO_LIST_FILTERS, createdTo: '2026-09-29T15:00' },
      }),
    ).toBe(false)
  })
})
