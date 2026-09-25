import { useState } from 'react'
import { kitchenServices, printingService, type kitchenOrders, type OrderItemStatus } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { getColorForTable } from '@/components/AvatarInitials'
import { Clock, TicketCheck } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { NEXT_ACTION_LABEL, NEXT_STATUS, STATUS_LABEL } from '../lib/itemStatus'
import { useTranslation } from '@/lib/i18n'

const COLUMNS: OrderItemStatus[] = ['PENDING', 'PREPARING', 'READY']
const BULK_TARGET_STATUSES: OrderItemStatus[] = ['PENDING', 'PREPARING', 'READY', 'DELIVERED']

export const FocusedCard = ({ order }: { order: kitchenOrders }) => {
  const queryClient = useQueryClient()
  const { t } = useTranslation('kitchen')
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

  const visibleItems = order.items?.filter((item) => item.status !== 'DELIVERED') ?? []

  const updateItemStatusMutation = useMutation({
    mutationFn: ({ itemId, status }: { itemId: string; status: OrderItemStatus }) =>
      kitchenServices.updateItemStatus(order.id!, itemId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kitchenOrders'] })
    },
    onError: () => {
      toast.error(t('itemStatusUpdateErrorToast'))
    },
  })

  const printTicketMutation = useMutation({
    mutationFn: () => printingService.printKitchenTicket(order.id!),
    onSuccess: (res) =>
      toast.success(res.status === 'PENDING' ? t('printQueuedNoAgentToast') : t('printSentToast')),
    onError: () => toast.error(t('printFailedToast')),
  })

  const bulkUpdateMutation = useMutation({
    mutationFn: (status: OrderItemStatus) =>
      kitchenServices.updateItemsStatus(order.id!, Array.from(selectedIds), status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kitchenOrders'] })
      setSelectedIds(new Set())
    },
    onError: () => {
      toast.error(t('itemStatusUpdateErrorToast'))
    },
  })

  const toggleItem = (itemId: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(itemId)) next.delete(itemId)
      else next.add(itemId)
      return next
    })
  }

  const allSelected = visibleItems.length > 0 && selectedIds.size === visibleItems.length

  const toggleSelectAll = () => {
    setSelectedIds(allSelected ? new Set() : new Set(visibleItems.map((item) => item.itemId!)))
  }

  return (
    <>
      <div className="flex flex-col gap-6 shrink-0">
        <Card
          className={`p-5 rounded-3xl border-l-8 ${getColorForTable(order.sessionId!)}`}
        >
          <CardHeader className="flex flex-col gap-2 border-b">
            <h2 className="text-2xl font-bold text-[#8c1717] tracking-tight">
              {t('orderDetailsHeading', { tableNumber: order.tableNumber ?? '' })}
            </h2>
            <div className="w-full flex items-center justify-between">
              <div className="flex gap-3">
                <span className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <TicketCheck />{' '}
                  {t('ticketLabel', { code: order.id!.substring(0, 6).toUpperCase() })}
                </span>
                <span className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <Clock /> {t('entryTimeLabel', { time: order.createdAt ?? '' })}
                </span>
              </div>

              <div className="flex flex-row items-center gap-3">
                {selectedIds.size > 0 && (
                  <Select
                    disabled={bulkUpdateMutation.isPending}
                    onValueChange={(value) => bulkUpdateMutation.mutate(value as OrderItemStatus)}
                  >
                    <SelectTrigger className="rounded-xl">
                      <SelectValue placeholder={t('kdsBulkStatusPlaceholder')} />
                    </SelectTrigger>
                    <SelectContent>
                      {BULK_TARGET_STATUSES.map((status) => (
                        <SelectItem key={status} value={status}>
                          {STATUS_LABEL[status]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
                <Button
                  size="sm"
                  variant="outline"
                  onClick={toggleSelectAll}
                  disabled={visibleItems.length === 0}
                >
                  {allSelected ? t('kdsDeselectAll') : t('kdsSelectAll')}
                </Button>
                <Button
                  className="p-6 "
                  disabled={printTicketMutation.isPending}
                  onClick={() => printTicketMutation.mutate()}
                >
                  {t('printButton')}
                </Button>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
              {COLUMNS.map((column) => {
                const columnItems = visibleItems.filter((item) => (item.status ?? 'PENDING') === column)
                const next = NEXT_STATUS[column]
                return (
                  <section key={column} className="flex flex-col gap-3 rounded-2xl bg-gray-50 p-3">
                    <header className="flex items-center justify-between px-1">
                      <h3 className="text-lg font-bold text-gray-800">{STATUS_LABEL[column]}</h3>
                      <Badge variant="secondary">{columnItems.length}</Badge>
                    </header>
                    {columnItems.length === 0 ? (
                      <p className="py-4 text-center text-sm text-gray-400">{t('kdsColumnEmpty')}</p>
                    ) : (
                      <ul className="flex flex-col gap-3">
                        {columnItems.map((item) => (
                          <li
                            key={item.itemId}
                            className="flex items-center gap-3 rounded-2xl border border-gray-200 bg-white px-4 py-2"
                          >
                            <Checkbox
                              className="rounded-full"
                              aria-label={t('kdsSelectItemAriaLabel', { name: item.name ?? '' })}
                              checked={selectedIds.has(item.itemId!)}
                              onCheckedChange={() => toggleItem(item.itemId!)}
                            />
                            <div className="flex flex-1 flex-col gap-1">
                              <span className="text-sm font-semibold text-gray-800">{item.name}</span>
                              {item.modifiers && item.modifiers.length > 0 && (
                                <span className="text-xs text-gray-500">{item.modifiers.join(', ')}</span>
                              )}
                            </div>
                            {next && (
                              <Button
                                size="sm"
                                variant="outline"
                                disabled={updateItemStatusMutation.isPending}
                                onClick={() =>
                                  updateItemStatusMutation.mutate({ itemId: item.itemId!, status: next })
                                }
                              >
                                {NEXT_ACTION_LABEL[column]}
                              </Button>
                            )}
                          </li>
                        ))}
                      </ul>
                    )}
                  </section>
                )
              })}
            </div>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
