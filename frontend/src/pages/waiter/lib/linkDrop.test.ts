import { describe, expect, test } from 'vitest'
import { resolveLinkDrop } from './linkDrop'

describe('resolveLinkDrop', () => {
  test('a free table dropped on an occupied one resolves to a link request', () => {
    expect(resolveLinkDrop('t5', { id: 't3', sessionId: 'sess-3' })).toEqual({ sessionId: 'sess-3', tableId: 't5' })
  })

  test('no drop target resolves to nothing', () => {
    expect(resolveLinkDrop('t5', null)).toBeNull()
  })

  test('dropping a table on itself resolves to nothing', () => {
    expect(resolveLinkDrop('t3', { id: 't3', sessionId: 'sess-3' })).toBeNull()
  })

  test('a target without a session (a free table) resolves to nothing', () => {
    expect(resolveLinkDrop('t5', { id: 't6' })).toBeNull()
  })
})
