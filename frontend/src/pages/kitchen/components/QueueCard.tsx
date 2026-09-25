import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { getColorForTable } from '@/components/AvatarInitials'
import { kitchenServices, type kitchenOrders, type OrderItemStatus } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Clock } from 'lucide-react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { NEXT_ACTION_LABEL, NEXT_STATUS } from '../lib/itemStatus'
import { useTranslation } from '@/lib/i18n'

const LATE_AFTER_MINUTES = 15

export const QueueCard = ({
  order,
  status,
  now,
}: {
  order: kitchenOrders
  status: OrderItemStatus
  now: number
}) => {
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

  const items = order.items?.filter((item) => (item.status ?? 'PENDING') === status) ?? []
  const next = NEXT_STATUS[status]
  const minutes = Math.max(0, Math.floor((now - new Date(order.createdAt ?? now).getTime()) / 60000))
  const isLate = minutes >= LATE_AFTER_MINUTES

  return (
    <Card className={`shrink-0 gap-2 border-l-7 py-3 ${getColorForTable(order.sessionId!)}`}>
      <CardHeader className='flex flex-row items-start justify-between'>
        <div>
          <CardTitle className='text-2xl font-black tracking-tight'>
            {order.tableNumber || '?'}
          </CardTitle>
          <p className='mt-1 text-xs text-gray-500'>
            {t('ticketLabel', { code: order.id?.substring(0, 6).toUpperCase() ?? '' })}
          </p>
        </div>
        <span
          className={`flex items-center gap-1 text-sm font-semibold ${isLate ? 'text-red-600' : 'text-gray-500'}`}
        >
          <Clock className='size-4' />
          {t('elapsedMinutes', { minutes })}
        </span>
      </CardHeader>
      <CardContent>
        <ul className='space-y-3'>
          {items.map((item) => (
            <li key={item.itemId} className='flex items-center justify-between gap-3'>
              <div className='flex flex-col'>
                <span className='text-sm font-semibold text-gray-800'>{item.name}</span>
                {item.modifiers && item.modifiers.length > 0 && (
                  <span className='text-xs text-gray-500'>{item.modifiers.join(', ')}</span>
                )}
              </div>
              {next && (
                <Button
                  size='sm'
                  variant='outline'
                  disabled={updateItemStatusMutation.isPending}
                  onClick={() => updateItemStatusMutation.mutate({ itemId: item.itemId!, status: next })}
                >
                  {NEXT_ACTION_LABEL[status]}
                </Button>
              )}
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  )
}
