import { describe, test, expect } from 'vitest'
import axios from 'axios'
import { extractPlanGateError } from './planGate'

describe('extractPlanGateError', () => {
  test('returns null for a non-axios error', () => {
    expect(extractPlanGateError(new Error('boom'))).toBeNull()
  })

  test('returns null when the axios error has a different code', () => {
    const error = new axios.AxiosError('fail', undefined, undefined, undefined, {
      status: 409,
      data: { code: 'CASH_SHIFT_OVERDUE' },
    } as never)
    expect(extractPlanGateError(error)).toBeNull()
  })

  test('extracts feature and requiredPlan when the code matches', () => {
    const error = new axios.AxiosError('fail', undefined, undefined, undefined, {
      status: 402,
      data: { code: 'PLAN_LIMIT_EXCEEDED', feature: 'export', requiredPlan: 'PRO' },
    } as never)
    expect(extractPlanGateError(error)).toEqual({ feature: 'export', requiredPlan: 'PRO' })
  })
})
