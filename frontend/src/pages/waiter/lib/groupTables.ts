import type { DashboardResponse } from '@/lib/api'

export type LinkedTableSummary = NonNullable<DashboardResponse['linkedTables']>[number]

export interface TableGroup {
  /** The card's table: the primary one when the group is merged. */
  table: DashboardResponse
  /** Tables attached to the primary; empty for an individual table. */
  linked: LinkedTableSummary[]
  /** Static Tailwind classes so the merged card spans the cells of the tables it joins. */
  spanClass: string
}

// The label drawn on a card: "M3" alone, "M3 + M4" when tables are merged into it.
export const tableCardLabel = (table: DashboardResponse, linked: LinkedTableSummary[]): string =>
  `M${table.tableNumber}${linked.map((l) => ` + M${l.tableNumber}`).join('')}`

// Literal class strings (not built dynamically) so Tailwind keeps them. The phone grid has 2
// columns and the sm+ grid 3, so a group never asks for more than the columns that exist.
const spanClassFor = (size: number): string => {
  if (size <= 1) return ''
  if (size === 2) return 'col-span-2'
  return 'col-span-2 sm:col-span-3'
}

// One card per group: a table attached to another table's session is folded into its primary's
// card. A linked table whose primary is not on the floor (e.g. deactivated mid-session) stays
// visible on its own instead of vanishing.
export const groupTables = (tables: DashboardResponse[] | undefined): TableGroup[] => {
  const list = tables ?? []
  const onFloor = new Set(list.map((table) => table.tableId))
  return list
    .filter((table) => !(table.linkedToTableId && onFloor.has(table.linkedToTableId)))
    .map((table) => {
      const linked = table.linkedToTableId ? [] : (table.linkedTables ?? [])
      return { table, linked, spanClass: spanClassFor(1 + linked.length) }
    })
}
