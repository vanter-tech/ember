import { ParticipantQrModal } from './components/ParticipantsQrModal'
import { DashboardService, SessionTableService, cashShiftService } from '@/lib/api'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import {
  DndContext,
  DragOverlay,
  MouseSensor,
  TouchSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { useAuthStore } from '@/store/authStore'
import { useWebsocketStore } from '@/store/websocket'
import { Button } from '@/components/ui/button'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Armchair } from 'lucide-react'
import { useUIStore } from '@/store/uiStore'
import { Link } from 'react-router-dom'
import { useTranslation } from '@/lib/i18n'
import { EmptyState } from '@/components/EmptyState'
import { WaiterTour } from './components/WaiterTour'
import { CardGridSkeleton } from '@/components/skeletons/CardGridSkeleton'
import { TableDetailsSkeleton } from './components/TableDetailsSkeleton'
import { FloorHeaderSkeleton } from './components/FloorHeaderSkeleton'
import { Skeleton } from '@/components/ui/skeleton'
import { tableSkeletonCount } from './lib/tableSkeletonCount'
import { TableCard } from './components/TableCard'
import { TableCardView } from './components/TableCardView'
import { LinkTableModal } from './components/LinkTableModal'
import { resolveLinkDrop } from './lib/linkDrop'
import { groupTables } from './lib/groupTables'
import { useFlipGrid } from './lib/useFlipGrid'

export const Tables = () => {
  const { t } = useTranslation('waiter')
  const { restaurantId, role } = useAuthStore()
  const [selectedTable, setSelectedTable] = useState<string | undefined>(
    undefined
  )

  const { openModal } = useUIStore()

  const { data: dashboardData, isPending: isLoadingDashboard } = useQuery({
    queryKey: ['dashboardData', restaurantId],
    queryFn: () => DashboardService.getDashboardData(),
    enabled: !!restaurantId,
  })

  const { data: cashShift } = useQuery({
    queryKey: ['cashShiftCurrent'],
    queryFn: cashShiftService.current,
    enabled: !!restaurantId,
  })

  const isCajaOpen = cashShift?.status === 'OPEN'
  // An ADMIN can always browse tables (to inspect or close one that got stuck, which is usually
  // also when the caja is closed); assigning a table still requires an open caja.
  const canBrowse = isCajaOpen || role === 'ADMIN'
  // The link endpoints are WAITER-only (no role hierarchy), so only a waiter is offered a merge.
  const canLink = role === 'WAITER'

  const tableDetails = dashboardData?.find(
    (data) => data.tableId === selectedTable
  )

  const sessionId = tableDetails?.currentSession?.sessionId

  const { data: sessionData } = useQuery({
    queryKey: ['sessionDetails', sessionId],
    queryFn: () => SessionTableService.sessionInformation(sessionId!),
    enabled: !!sessionId,
  })

  const queryClient = useQueryClient()
  const unlinkMutation = useMutation({
    mutationFn: ({ sessionId, tableId }: { sessionId: string; tableId: string }) =>
      SessionTableService.unlinkTable(sessionId, tableId),
    onSuccess: (_data, vars) => {
      const unlinked = tableDetails?.linkedTables?.find((l) => l.tableId === vars.tableId)
      toast.success(t('unlinkSuccessToast', { table: unlinked?.tableNumber ?? '' }))
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
    },
    onError: () => toast.error(t('unlinkErrorToast')),
  })

  // Mouse: a short drag distance (so a click still selects). Touch: press-and-hold, so swiping the
  // grid still scrolls the page.
  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 8 } }),
    useSensor(TouchSensor, { activationConstraint: { delay: 350, tolerance: 8 } }),
  )
  const [draggingId, setDraggingId] = useState<string | null>(null)
  const linkMutation = useMutation({
    mutationFn: ({ sessionId, tableId }: { sessionId: string; tableId: string }) =>
      SessionTableService.linkTable(sessionId, tableId),
    onSuccess: (_data, vars) => {
      const linked = dashboardData?.find((table) => table.tableId === vars.tableId)
      toast.success(t('linkSuccessToast', { table: linked?.tableNumber ?? '' }))
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
    },
    onError: () => {
      toast.error(t('linkErrorToast'))
      queryClient.invalidateQueries({ queryKey: ['dashboardData'] })
    },
  })

  const handleDragStart = (event: DragStartEvent) => setDraggingId(String(event.active.id))
  const handleDragEnd = (event: DragEndEvent) => {
    setDraggingId(null)
    const request = resolveLinkDrop(
      String(event.active.id),
      event.over ? { id: String(event.over.id), sessionId: event.over.data.current?.sessionId } : null,
    )
    if (request) linkMutation.mutate(request)
  }
  const draggingTable = dashboardData?.find((table) => table.tableId === draggingId)

  // Placeholder tables while the floor loads: as many as fill the window (see tableSkeletonCount).
  const skeletonCount = useMemo(() => tableSkeletonCount(window.innerWidth, window.innerHeight), [])

  // Merged tables are one wide card; the grid animates cards sliding together or apart.
  const groups = useMemo(() => groupTables(dashboardData), [dashboardData])
  const gridRef = useRef<HTMLDivElement>(null)
  const layoutSignature = groups
    .map((g) => `${g.table.tableId}:${g.spanClass}:${g.table.isOccupied ? 1 : 0}:${g.linked.map((l) => l.tableId).join('+')}`)
    .join('|')
  useFlipGrid(gridRef, layoutSignature)

  const { isConnected, stompClient, subscribeToWaiterSession, unsubscribeFromWaiterSession } =
    useWebsocketStore()

  useEffect(() => {
    if (sessionId && isConnected && stompClient?.connected) {
      subscribeToWaiterSession(sessionId)
    }

    return () => {
      unsubscribeFromWaiterSession()
    }
  }, [
    sessionId,
    isConnected,
    stompClient,
    subscribeToWaiterSession,
    unsubscribeFromWaiterSession,
  ])

  const itemsToWaiter = sessionData?.items
    ? sessionData.items.filter((item) => item.status != 'DRAFT')
    : []

  return (
    <div className="flex flex-col md:flex-row w-full h-full gap-5 p-5">
      <div className="w-full md:w-[70%]">
        {isLoadingDashboard ? (
          <FloorHeaderSkeleton />
        ) : (
        <div className="flex justify-between mb-5">
          <h2>{t('mainRoomTitle')}</h2>
          <div className="flex gap-4">
            <div className="flex items-center gap-2">
              <div className="w-5 h-5 bg-[#8c1717] rounded-full"></div>
              <span className="text-md text-zinc-500">{t('statusOccupied')}</span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-5 h-5 bg-[#6b6161] rounded-full"></div>
              <span className="text-md text-zinc-500">{t('statusFree')}</span>
            </div>
          </div>
        </div>
        )}

        {!isLoadingDashboard && canLink && <p className="mb-3 text-xs text-zinc-500">{t('linkDragHint')}</p>}

        {isLoadingDashboard ? (
          <div id="waiter-tour-grid">
            <CardGridSkeleton label={t('loadingDashboard')} count={skeletonCount} />
          </div>
        ) : (
        <DndContext
          sensors={sensors}
          onDragStart={handleDragStart}
          onDragEnd={handleDragEnd}
          onDragCancel={() => setDraggingId(null)}
        >
        <div ref={gridRef} id="waiter-tour-grid" className="grid grid-cols-2 sm:grid-cols-3 grid-flow-dense gap-4 relative">
          {!canBrowse && (
            <div className="absolute inset-0 z-10 flex items-center justify-center rounded-2xl bg-white/40">
              <span className="max-w-[80%] text-center text-lg font-semibold text-[#8c1717]">
                {t('needOpenCajaOverlay')}
              </span>
            </div>
          )}
          {isCajaOpen && (dashboardData?.length ?? 0) === 0 && (
            <div className="col-span-full">
              <EmptyState
                icon={Armchair}
                title={t('tablesEmptyTitle')}
                description={t('tablesEmptyDescription')}
              />
            </div>
          )}
          {groups.map(({ table, linked, spanClass }) => (
            <TableCard
              key={table.tableId}
              table={table}
              linked={linked}
              spanClass={spanClass}
              selected={table.tableId === selectedTable}
              canBrowse={canBrowse}
              onSelect={() => canBrowse && setSelectedTable(table.tableId)}
              draggable={canLink && canBrowse && isCajaOpen && !table.isOccupied}
              dropTarget={!!table.isOccupied && !table.linkedToTableId && !!table.currentSession?.sessionId}
            />
          ))}
        </div>
        <DragOverlay>
          {draggingTable ? (
            // The whole card, same box as the one being dragged (the overlay wrapper takes its size).
            <TableCardView
              table={draggingTable}
              linked={[]}
              className="h-full w-full cursor-grabbing shadow-2xl ring-2 ring-[#8c1717] rotate-2"
            />
          ) : null}
        </DragOverlay>
        </DndContext>
        )}
      </div>
      <div id="waiter-tour-panel" className="w-full md:w-[30%] border-t md:border-t-0 md:border-l border-zinc-200 pt-5 md:pt-0 md:pl-5">
        {isLoadingDashboard ? (
          <div>
            <Skeleton data-testid="skeleton-panel-title" className="mb-5 h-7 w-40" />
            <TableDetailsSkeleton />
          </div>
        ) : tableDetails ? (
          <div>
            <h2 className="text-xl font-semibold mb-5">{t('tableDetailsTitle')}</h2>
            <div className="bg-white rounded-2xl p-6">
              <div className="flex justify-between items-center">
                <div className="flex flex-col">
                  <h2 className="text-[#8c1717] font-bold text-3xl">
                    M{tableDetails.tableNumber}
                  </h2>
                  {tableDetails.linkedToTableId && (
                    <span className="text-xs text-zinc-500">
                      {t('linkedToLabel', { table: tableDetails.linkedToTableNumber ?? '?' })}
                    </span>
                  )}
                </div>
                <div className="flex flex-col gap-2 text-right">
                  <span className="text-xs text-zinc-500">{t('waiterLabel')}</span>
                  {tableDetails.currentSession?.waiterName || t('unassignedLabel')}
                </div>
              </div>
              <div className="flex items-center gap-2 mt-4">
                {!tableDetails.isOccupied ? (
                  <div className="flex items-center gap-2">
                    <div className="w-5 h-5 bg-[#6b6161] rounded-full"></div>
                    <span className="text-md text-zinc-500">{t('statusFree')}</span>
                  </div>
                ) : (
                  <div className="flex items-center gap-2">
                    <div className="w-5 h-5 bg-[#8c1717] rounded-full"></div>
                    <span className="text-md text-zinc-500">{t('statusOccupied')}</span>
                  </div>
                )}
              </div>
              <div className="my-6 border-y border-zinc-100 py-4">
                {itemsToWaiter.length > 0 ? (
                  <div className="flex flex-col gap-3 max-h-50 overflow-y-auto pr-1">
                    {itemsToWaiter.map((item) => (
                      <div
                        key={item.id}
                        className="flex items-center justify-between"
                      >
                        <div className="flex flex-col">
                          <span className="text-sm font-semibold">
                            {item.name}
                          </span>
                          <span className="text-xs text-zinc-500">
                            {item.participantName}
                          </span>
                        </div>
                        <span className="text-sm font-bold text-[#8c1717]">
                          ${item.price?.toFixed(2)}
                        </span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <span>{t('noOrderCurrently')}</span>
                )}
              </div>
              <div className="flex flex-col gap-4 mt-6">
                {canLink && tableDetails.isOccupied && !tableDetails.linkedToTableId && (
                  <>
                    {(tableDetails.linkedTables?.length ?? 0) > 0 && (
                      <div className="flex flex-col gap-3">
                        <span className="text-xs text-zinc-500">{t('linkedTablesHeading')}</span>
                        {tableDetails.linkedTables!.map((linked) => (
                          <div key={linked.tableId} className="flex items-center justify-between gap-3">
                            <span className="text-lg font-semibold">M{linked.tableNumber}</span>
                            <Button
                              variant="outline"
                              className="px-5 text-md"
                              aria-label={t('unlinkAria', { table: linked.tableNumber ?? '' })}
                              disabled={unlinkMutation.isPending}
                              onClick={() =>
                                unlinkMutation.mutate({
                                  sessionId: tableDetails.currentSession!.sessionId!,
                                  tableId: linked.tableId!,
                                })
                              }
                            >
                              {t('unlinkButton')}
                            </Button>
                          </div>
                        ))}
                      </div>
                    )}
                    <Button
                      variant="outline"
                      className="w-full text-md"
                      onClick={() =>
                        openModal('LINK_TABLE', {
                          sessionId: tableDetails.currentSession?.sessionId,
                          tableNumber: tableDetails.tableNumber,
                        })
                      }
                    >
                      {t('linkTableButton')}
                    </Button>
                  </>
                )}
                {tableDetails.isOccupied && (
                  <Link to={tableDetails.currentSession?.sessionId + ''}>
                    <Button className="w-full text-md">{t('viewInfoButton')}</Button>
                  </Link>
                )}
                <Button
                  id="waiter-tour-assign"
                  className="w-full text-md"
                  disabled={!isCajaOpen || tableDetails.isOccupied}
                  onClick={(e) => {
                    openModal('PARTICIPANTS_QR', tableDetails)
                    e.preventDefault()
                    e.stopPropagation()
                  }}
                >
                  {t('assignTableLabel')}
                </Button>
              </div>
            </div>
          </div>
        ) : (
          <div className="flex h-full items-center justify-center text-zinc-500">
            {t('selectTablePrompt')}
          </div>
        )}
      </div>
      <ParticipantQrModal />
      <LinkTableModal />
      <WaiterTour
        tableIds={
          dashboardData
            ?.map((table) => table.tableId)
            .filter((id): id is string => Boolean(id)) ?? []
        }
        onSelectFirstTable={() => {
          if (dashboardData?.[0]) {
            setSelectedTable(dashboardData[0].tableId)
          }
        }}
      />
    </div>
  )
}
