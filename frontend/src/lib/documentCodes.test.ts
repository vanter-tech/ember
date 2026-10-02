import { describe, expect, test } from 'vitest'
import { billCode, kitchenTicketCode } from './documentCodes'

describe('billCode', () => {
  test('shows the frozen code when the bill has one', () => {
    expect(billCode('ELPO-000123', 9)).toBe('ELPO-000123')
  })

  test('falls back to the raw id for a bill issued before numbering', () => {
    expect(billCode(undefined, 9)).toBe('#9')
    expect(billCode(null, 9)).toBe('#9')
  })

  test('is empty when there is nothing to show', () => {
    expect(billCode(undefined, undefined)).toBe('')
  })
})

describe('kitchenTicketCode', () => {
  test('shows the ticket code when the order has one', () => {
    expect(kitchenTicketCode({ ticketCode: 'ELPO-KDS-000045', id: 'abcdef12-0000' })).toBe('ELPO-KDS-000045')
  })

  test('falls back to the first 6 characters of the id for an order without a code', () => {
    expect(kitchenTicketCode({ id: 'abcdef12-0000' })).toBe('#ABCDEF')
  })

  test('is empty when there is no id either', () => {
    expect(kitchenTicketCode({})).toBe('')
  })
})
