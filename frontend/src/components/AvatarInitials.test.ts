import { describe, expect, test } from 'vitest'
import { getAvatarColor } from '@/components/AvatarInitials'

describe('getAvatarColor', () => {
  test('is deterministic for the same seed', () => {
    expect(getAvatarColor('user-1')).toBe(getAvatarColor('user-1'))
  })

  test('spreads different seeds across several colors', () => {
    const colors = new Set(['Ana', 'Beto', 'Carla', 'Diego', 'Elena', 'Fabio'].map(getAvatarColor))
    expect(colors.size).toBeGreaterThan(2)
  })

  test('falls back for an empty seed', () => {
    expect(getAvatarColor('')).toBeTruthy()
    expect(getAvatarColor(undefined)).toBe(getAvatarColor(null))
  })
})
