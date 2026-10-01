import { describe, expect, test } from 'vitest'
import { mergedTableLabel } from './mergedTableLabel'

describe('mergedTableLabel', () => {
  test('is null for an individual table', () => {
    expect(mergedTableLabel(3, [], 'Unidas')).toBeNull()
    expect(mergedTableLabel(3, undefined, 'Unidas')).toBeNull()
  })

  test('joins the primary and every linked table in order, then the word', () => {
    expect(mergedTableLabel(3, [4], 'Unidas')).toBe('M3+M4 - Unidas')
    expect(mergedTableLabel(3, [4, 5], 'Unidas')).toBe('M3+M4+M5 - Unidas')
  })

  test('is null when the primary number is unknown', () => {
    expect(mergedTableLabel(undefined, [4], 'Unidas')).toBeNull()
  })
})
