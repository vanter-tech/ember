import { useState } from 'react'
import { kitchenServices, type kitchenOrders, type OrderItemStatus } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { getColorForTable } from '@/components/AvatarInitials'
import { Clock, TicketCheck, UserCheck } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { NEXT_ACTION_LABEL, NEXT_STATUS, STATUS_LABEL } from '../lib/itemStatus'
import { useTranslation } from '@/lib/i18n'

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
                  <UserCheck /> {t('clientPlaceholder')}
                </span>
                <span className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <Clock /> {t('entryTimeLabel', { time: order.createdAt ?? '' })}
                </span>
              </div>

              <div className="flex flex-row gap-3">
                <Button className="p-6 ">{t('printButton')}</Button>
                <Button className="p-6 " variant={'destructive'}>
                  {t('voidButton')}
                </Button>
              </div>
            </div>
            <div className="w-full flex items-center gap-3">
              <Button
                size="sm"
                variant="outline"
                onClick={toggleSelectAll}
                disabled={visibleItems.length === 0}
              >
                {allSelected ? t('kdsDeselectAll') : t('kdsSelectAll')}
              </Button>
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
            </div>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-wrap gap-3">
              {visibleItems.map((item) => {
                const status = item.status ?? 'PENDING'
                const next = NEXT_STATUS[status]
                return (
                  <li
                    key={item.itemId}
                    className="flex items-center gap-3 rounded-2xl border border-gray-200 px-4 py-2"
                  >
                    <Checkbox
                      className="rounded-full"
                      aria-label={t('kdsSelectItemAriaLabel', { name: item.name ?? '' })}
                      checked={selectedIds.has(item.itemId!)}
                      onCheckedChange={() => toggleItem(item.itemId!)}
                    />
                    <div className="flex flex-col gap-1">
                      <span className="text-sm font-semibold text-gray-800">
                        {item.name}
                      </span>
                      {item.modifiers && item.modifiers.length > 0 && (
                        <span className="text-xs text-gray-500">
                          {item.modifiers.join(', ')}
                        </span>
                      )}
                      <Badge variant="outline" className="w-fit">
                        {STATUS_LABEL[status]}
                      </Badge>
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
                        {NEXT_ACTION_LABEL[status]}
                      </Button>
                    )}
                  </li>
                )
              })}
            </ul>
          </CardContent>
        </Card>
      </div>
    </>
  )
}
