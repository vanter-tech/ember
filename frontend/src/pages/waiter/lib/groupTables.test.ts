import { describe, expect, test } from 'vitest'
import { groupTables } from './groupTables'
import type { DashboardResponse } from '@/lib/api'

const free = (id: string, n: number): DashboardResponse => ({ tableId: id, tableNumber: n, isOccupied: false, linkedTables: [] })
const primary = (id: string, n: number, linked: { tableId: string; tableNumber: number }[]): DashboardResponse => ({
  tableId: id, tableNumber: n, isOccupied: true, linkedTables: linked,
})
const linkedTo = (id: string, n: number, primaryId: string, primaryNumber: number): DashboardResponse => ({
  tableId: id, tableNumber: n, isOccupied: true, linkedToTableId: primaryId, linkedToTableNumber: primaryNumber, linkedTables: [],
})

describe('groupTables', () => {
  test('without merges every table keeps its place and a one-cell span', () => {
    const groups = groupTables([free('t1', 1), free('t2', 2)])

    expect(groups.map((g) => g.table.tableId)).toEqual(['t1', 't2'])
    expect(groups.every((g) => g.linked.length === 0 && g.spanClass === '')).toBe(true)
  })

  test('a linked table disappears as its own cell and widens its primary to two cells', () => {
    const groups = groupTables([
      free('t1', 1),
      primary('t3', 3, [{ tableId: 't4', tableNumber: 4 }]),
      linkedTo('t4', 4, 't3', 3),
      free('t5', 5),
    ])

    expect(groups.map((g) => g.table.tableId)).toEqual(['t1', 't3', 't5'])
    const merged = groups[1]
    expect(merged.linked).toEqual([{ tableId: 't4', tableNumber: 4 }])
    expect(merged.spanClass).toBe('col-span-2')
  })

  test('three tables take two columns on the phone grid and three from sm up', () => {
    const groups = groupTables([
      primary('t3', 3, [
        { tableId: 't4', tableNumber: 4 },
        { tableId: 't5', tableNumber: 5 },
      ]),
      linkedTo('t4', 4, 't3', 3),
      linkedTo('t5', 5, 't3', 3),
    ])

    expect(groups).toHaveLength(1)
    expect(groups[0].spanClass).toBe('col-span-2 sm:col-span-3')
  })

  test('a linked table whose primary is not on the floor (deactivated) stays visible on its own', () => {
    const groups = groupTables([linkedTo('t4', 4, 't3', 3)])

    expect(groups).toHaveLength(1)
    expect(groups[0].table.tableId).toBe('t4')
    expect(groups[0].linked).toEqual([])
    expect(groups[0].spanClass).toBe('')
  })

  test('no data yields no groups', () => {
    expect(groupTables(undefined)).toEqual([])
  })
})
