import { describe, test, expect } from 'vitest'
import { NICARAGUA_DENOMINATIONS, sumBreakdown } from './denominations'

describe('denominations', () => {
  test('has 14 entries, 7 bills and 7 coins', () => {
    expect(NICARAGUA_DENOMINATIONS).toHaveLength(14)
    expect(NICARAGUA_DENOMINATIONS.filter((d) => d.kind === 'BILL')).toHaveLength(7)
    expect(NICARAGUA_DENOMINATIONS.filter((d) => d.kind === 'COIN')).toHaveLength(7)
  })

  test('the C$10 bill and C$10 coin are distinct entries with the same value', () => {
    const bill = NICARAGUA_DENOMINATIONS.find((d) => d.id === 'bill_10')
    const coin = NICARAGUA_DENOMINATIONS.find((d) => d.id === 'coin_10')
    expect(bill?.value).toBe(10)
    expect(coin?.value).toBe(10)
    expect(bill?.kind).toBe('BILL')
    expect(coin?.kind).toBe('COIN')
  })

  test('sumBreakdown multiplies value by quantity across rows', () => {
    const total = sumBreakdown([
      { denominationId: 'bill_100', quantity: 2 },
      { denominationId: 'coin_5', quantity: 3 },
    ])
    expect(total).toBe(215)
  })

  test('sumBreakdown ignores an unknown denominationId rather than throwing', () => {
    const total = sumBreakdown([{ denominationId: 'nope', quantity: 5 }])
    expect(total).toBe(0)
  })

  test('sumBreakdown of an empty array is 0', () => {
    expect(sumBreakdown([])).toBe(0)
  })
})
