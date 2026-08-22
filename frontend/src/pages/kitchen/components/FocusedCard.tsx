import { kitchenServices, type kitchenOrders, type OrderItemStatus } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader } from '@/components/ui/card'
import { getColorForTable } from '@/components/AvatarInitials'
import { Clock, TicketCheck, UserCheck } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { NEXT_ACTION_LABEL, NEXT_STATUS, STATUS_LABEL } from '../lib/itemStatus'
import { useTranslation } from '@/lib/i18n'

export const FocusedCard = ({ order }: { order: kitchenOrders }) => {
  const queryClient = useQueryClient()
  const { t } = useTranslation('kitchen')

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
          </CardHeader>
          <CardContent>
            <ul className="flex flex-wrap gap-3">
              {order.items?.filter((item) => item.status !== 'DELIVERED').map((item) => {
                const status = item.status ?? 'PENDING'
                const next = NEXT_STATUS[status]
                return (
                  <li
                    key={item.itemId}
                    className="flex items-center gap-3 rounded-2xl border border-gray-200 px-4 py-2"
                  >
                    <div className="flex flex-col gap-1">
                      <span className="text-sm font-semibold text-gray-800">
                        {item.name}
                      </span>
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
