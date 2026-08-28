import { describe, it, expect } from 'vitest'
import { deriveCashShiftAlert, PRE_WARNING_MS } from './cashShiftAlert'
import type { CashShiftResponse } from './api'

const base = (over: Partial<CashShiftResponse>): CashShiftResponse =>
  ({
    id: 1,
    status: 'OPEN',
    overdue: false,
    businessDay: '2026-08-28',
    effectiveDeadline: '2026-08-28T23:00:00',
    expiresAt: '2026-08-28T23:00:00',
    prolongCount: 0,
    ...over,
  }) as CashShiftResponse

describe('deriveCashShiftAlert', () => {
  const now = new Date('2026-08-28T12:00:00')

  it('returns IDLE when there is no shift', () => {
    expect(deriveCashShiftAlert(null, now)).toBe('IDLE')
  })

  it('returns IDLE well before the deadline', () => {
    expect(deriveCashShiftAlert(base({}), now)).toBe('IDLE')
  })

  it('returns PRE_WARNING inside the 30-minute window before the deadline', () => {
    const deadline = new Date(now.getTime() + PRE_WARNING_MS - 60_000).toISOString()
    expect(deriveCashShiftAlert(base({ effectiveDeadline: deadline }), now)).toBe('PRE_WARNING')
  })

  it('returns OVERDUE when the server flags it, regardless of clock', () => {
    expect(deriveCashShiftAlert(base({ overdue: true }), now)).toBe('OVERDUE')
  })

  it('returns STALE when businessDay is before today (local), outranking OVERDUE', () => {
    expect(
      deriveCashShiftAlert(base({ businessDay: '2026-08-27', overdue: true }), now)
    ).toBe('STALE')
  })
})
