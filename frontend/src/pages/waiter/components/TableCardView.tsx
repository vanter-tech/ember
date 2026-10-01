import type { ComponentProps } from 'react'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { Armchair, Users } from 'lucide-react'
import { AvatarInitials, getAvatarColor } from '@/components/AvatarInitials'
import { useTranslation } from '@/lib/i18n'
import type { DashboardResponse } from '@/lib/api'
import { tableCardLabel, type LinkedTableSummary } from '../lib/groupTables'

interface TableCardViewProps extends ComponentProps<'div'> {
  table: DashboardResponse
  /** Tables merged into this card (empty for an individual table). */
  linked: LinkedTableSummary[]
  selected?: boolean
  canBrowse?: boolean
}

// The card exactly as drawn on the grid. Presentation only (no drag-and-drop hooks), so the same
// card can also be rendered inside the DragOverlay, under the finger, without registering a second
// draggable with the same id. Extra props (ref, dnd listeners, data attributes) go to the card.
export const TableCardView = ({
  table,
  linked,
  selected = false,
  canBrowse = true,
  className = '',
  ...props
}: TableCardViewProps) => {
  const { t } = useTranslation('waiter')
  const isLinked = !!table.linkedToTableId

  return (
    <Card
      {...props}
      className={`${selected ? 'shadow-[0_0_8px_1px_rgba(140,23,23,0.35)]' : 'shadow-sm'} border-zinc-100
        h-40 flex flex-col justify-between rounded-2xl relative
        ${canBrowse ? 'cursor-pointer' : 'pointer-events-none cursor-not-allowed blur-sm'}
        ${table.isOccupied ? 'border-2 bg-[#8c1717] text-white' : 'bg-white text-black'}
        ${className}`}
    >
      {selected && (
        <div className="pointer-events-none absolute inset-0 z-20 animate-pulse rounded-2xl border-2 border-[#8c1717] shadow-[inset_0_0_0_3px_white,inset_0_0_6px_1px_rgba(140,23,23,0.35)]" />
      )}
      {isLinked ? (
        <span className="absolute bottom-4 left-4 z-10 rounded-full bg-white/90 px-2 py-0.5 text-[11px] font-bold text-[#8c1717]">
          {t('linkedToLabel', { table: table.linkedToTableNumber ?? '?' })}
        </span>
      ) : (
        table.isOccupied &&
        table.currentSession?.waiterName && (
          <div
            title={table.currentSession.waiterName}
            className={`absolute bottom-4 left-4 z-10 flex size-7 items-center justify-center rounded-full text-[11px] font-bold ${getAvatarColor(table.currentSession.waiterName)}`}
          >
            {AvatarInitials(table.currentSession.waiterName)}
          </div>
        )
      )}
      <CardHeader className="p-4 pb-0 flex justify-between">
        <span className=" text-2xl font-bold">{tableCardLabel(table, linked)}</span>
        {!isLinked && (
          <div
            className={`flex items-center justify-center gap-1 rounded-full h-6 w-11 bg-white text-black ${table.isOccupied ? 'border-2 border-[#8b0000]' : ''}`}
          >
            <Users className="h-4 w-4" />
            {table.isOccupied ? table.currentSession?.currentParticipant : '0'}
          </div>
        )}
      </CardHeader>
      <div className="absolute inset-0 pointer-events-none flex items-center justify-center gap-3">
        {Array.from({ length: 1 + linked.length }, (_, i) => (
          <Armchair key={i} className={`${table.isOccupied ? 'text-white' : 'text-zinc-400'} w-7 h-7`} />
        ))}
      </div>
      {table.isOccupied && <CardContent className="p-4"></CardContent>}
    </Card>
  )
}
