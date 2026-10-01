import { useDndContext, useDraggable, useDroppable } from '@dnd-kit/core'
import type { DashboardResponse } from '@/lib/api'
import { tableCardLabel, type LinkedTableSummary } from '../lib/groupTables'
import { TableCardView } from './TableCardView'

interface TableCardProps {
  table: DashboardResponse
  selected: boolean
  canBrowse: boolean
  onSelect: () => void
  /** A free table the waiter can press-and-hold to drag onto an occupied one. */
  draggable: boolean
  /** An occupied primary table that accepts a dragged free table. */
  dropTarget: boolean
  /** Tables merged into this card (empty for an individual table). */
  linked: LinkedTableSummary[]
  /** Grid span classes so a merged card covers the cells of the tables it joins. */
  spanClass: string
}

// The grid card with its drag-and-drop behaviour. What it looks like lives in TableCardView.
export const TableCard = ({ table, selected, canBrowse, onSelect, draggable, dropTarget, linked, spanClass }: TableCardProps) => {
  const { active } = useDndContext()
  const drag = useDraggable({ id: table.tableId!, disabled: !draggable })
  const drop = useDroppable({
    id: table.tableId!,
    disabled: !dropTarget,
    data: { sessionId: table.currentSession?.sessionId },
  })
  const dragging = active != null
  const isBeingDragged = active?.id === table.tableId
  // Only free tables carry the draggable attributes/listeners; nothing animates at rest.
  const dragProps = draggable ? { ...drag.attributes, ...drag.listeners } : {}

  return (
    <TableCardView
      ref={(node: HTMLDivElement | null) => {
        drag.setNodeRef(node)
        drop.setNodeRef(node)
      }}
      {...dragProps}
      table={table}
      linked={linked}
      selected={selected}
      canBrowse={canBrowse}
      data-flip-id={table.tableId}
      data-flip-members={linked.map((l) => l.tableId).join(',')}
      data-flip-label={tableCardLabel(table, linked)}
      data-flip-occupied={String(!!table.isOccupied)}
      onClick={onSelect}
      className={`${spanClass}
        ${draggable ? 'touch-manipulation select-none' : ''}
        ${isBeingDragged ? 'opacity-40' : ''}
        ${dragging && !isBeingDragged && !dropTarget ? 'opacity-50' : ''}
        ${dragging && dropTarget ? 'motion-safe:animate-pulse ring-2 ring-[#8c1717]' : ''}
        ${drop.isOver ? 'ring-4 ring-[#8c1717]' : ''}`}
    />
  )
}
