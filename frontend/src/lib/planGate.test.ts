import { describe, test, expect } from 'vitest'
import axios from 'axios'
import { extractPlanGateError, extractPlanGateErrorFromBlob } from './planGate'

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

describe('extractPlanGateErrorFromBlob', () => {
  test('returns null for a non-axios error', async () => {
    expect(await extractPlanGateErrorFromBlob(new Error('boom'))).toBeNull()
  })

  test('parses a blob error body and extracts feature/requiredPlan on a match', async () => {
    const blob = new Blob(
      [JSON.stringify({ code: 'PLAN_LIMIT_EXCEEDED', feature: 'export', requiredPlan: 'PRO' })],
      { type: 'application/problem+json' },
    )
    const error = new axios.AxiosError('fail', undefined, undefined, undefined, {
      status: 402,
      data: blob,
    } as never)
    expect(await extractPlanGateErrorFromBlob(error)).toEqual({ feature: 'export', requiredPlan: 'PRO' })
  })

  test('returns null when the blob body has a different code', async () => {
    const blob = new Blob([JSON.stringify({ code: 'SOME_OTHER_CODE' })], {
      type: 'application/problem+json',
    })
    const error = new axios.AxiosError('fail', undefined, undefined, undefined, {
      status: 500,
      data: blob,
    } as never)
    expect(await extractPlanGateErrorFromBlob(error)).toBeNull()
  })
})
