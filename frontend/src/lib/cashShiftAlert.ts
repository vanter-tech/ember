import type { CashShiftResponse } from './api'

export type CashShiftAlert = 'IDLE' | 'PRE_WARNING' | 'OVERDUE' | 'STALE'

export const PRE_WARNING_MS = 30 * 60_000
export const REMINDER_INTERVAL_MS = 15 * 60_000

const localYmd = (d: Date): string =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

export const deriveCashShiftAlert = (
  shift: CashShiftResponse | null,
  now: Date
): CashShiftAlert => {
  if (!shift) return 'IDLE'

  if (shift.businessDay && shift.businessDay < localYmd(now)) return 'STALE'
  if (shift.overdue) return 'OVERDUE'

  if (shift.effectiveDeadline) {
    const deadline = new Date(shift.effectiveDeadline).getTime()
    if (now.getTime() >= deadline - PRE_WARNING_MS && now.getTime() < deadline) {
      return 'PRE_WARNING'
    }
  }
  return 'IDLE'
}
