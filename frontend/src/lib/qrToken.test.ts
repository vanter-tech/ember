import { describe, test, expect } from 'vitest'
import { tokenFromScannedValue, sessionIdFromQrToken } from '@/lib/qrToken'

// A well-formed JWT whose payload base64-encodes { "sub": "sess-99" }.
const JWT = `h.${btoa('{"sub":"sess-99"}')}.s`

describe('sessionIdFromQrToken', () => {
  test('returns the sub claim', () => {
    expect(sessionIdFromQrToken(JWT)).toBe('sess-99')
  })
  test('null for a non-JWT', () => {
    expect(sessionIdFromQrToken('not-a-jwt')).toBeNull()
  })
})

describe('tokenFromScannedValue', () => {
  test('extracts the token from a full menu/join URL', () => {
    expect(
      tokenFromScannedValue(`https://app.ember.vanter.net/menu/join?token=${JWT}`),
    ).toBe(JWT)
  })

  test('accepts a bare JWT', () => {
    expect(tokenFromScannedValue(`  ${JWT}  `)).toBe(JWT)
  })

  test('null for a URL without a valid token param', () => {
    expect(tokenFromScannedValue('https://example.com/menu/join?token=abc')).toBeNull()
    expect(tokenFromScannedValue('https://example.com/')).toBeNull()
  })

  test('null for arbitrary QR content', () => {
    expect(tokenFromScannedValue('hello world')).toBeNull()
    expect(tokenFromScannedValue('')).toBeNull()
  })
})
