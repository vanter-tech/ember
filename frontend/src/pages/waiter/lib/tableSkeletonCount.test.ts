import { describe, expect, test } from 'vitest'
import { tableSkeletonCount } from './tableSkeletonCount'

describe('tableSkeletonCount', () => {
  test('desktop: 3 columns, enough rows to fill the viewport height', () => {
    expect(tableSkeletonCount(1024, 768)).toBe(12) // 3 cols x 4 rows
    expect(tableSkeletonCount(2124, 1021)).toBe(15) // 3 cols x 5 rows
  })

  test('phone: 2 columns', () => {
    expect(tableSkeletonCount(390, 844)).toBe(8) // 2 cols x 4 rows
  })

  test('never fewer than 4 rows nor more than 8, whatever the window', () => {
    expect(tableSkeletonCount(1200, 300)).toBe(12) // 3 x 4 (minimum)
    expect(tableSkeletonCount(1200, 5000)).toBe(24) // 3 x 8 (maximum)
  })
})
