import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from '@/components/ui/card'
import { getColorForTable } from '@/components/AvatarInitials'
import { kitchenServices, type kitchenOrders, type OrderItemStatus } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { NEXT_ACTION_LABEL, NEXT_STATUS, STATUS_LABEL } from '../lib/itemStatus'

export const QueueCard = ({order}: {order: kitchenOrders}) => {
  const queryClient = useQueryClient()

  const updateItemStatusMutation = useMutation({
    mutationFn: ({ itemId, status }: { itemId: string; status: OrderItemStatus }) =>
      kitchenServices.updateItemStatus(order.id!, itemId, status),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['kitchenOrders'] })
    },
    onError: () => {
      toast.error('No se pudo actualizar el estado del plato')
    },
  })

  return(
    <>
    <Card className={`flex shrink-0 border-l-7 ${getColorForTable(order.sessionId!)} h-[300px]` }>
        <CardHeader className='flex justify-between items-start'>
            <div>
                <CardTitle className='text-2xl font-black tracking-tight'>
                    {order.tableNumber || "?"}
                </CardTitle>
                <p className='text-xs text-gray-500 mt-1'>
                    Ticket: #{order.id?.substring(0,6).toUpperCase()}
                </p>
            </div>
        </CardHeader>
        <CardContent className='flex-1 overflow-y-auto px-6'>
            <ul className='space-y-4'>
                {order.items?.map((item) => {
                  const status = item.status ?? 'PENDING'
                  const next = NEXT_STATUS[status]
                  return (
                    <li key={item.itemId} className='flex justify-between items-center gap-3'>
                        <div className='flex flex-col gap-1'>
                            <span className='text-sm font-semibold text-gray-800'>
                                {item.name}
                            </span>
                            <Badge variant='outline' className='w-fit'>{STATUS_LABEL[status]}</Badge>
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
                  )
                })}
            </ul>
        </CardContent>
        <CardFooter className='mt-auto'>
            <Button className='w-full py-2 hover:bg-red-800 font-medium transition-colors'>
                Ver detalles
            </Button>
        </CardFooter>
    </Card>
    </>
  )
}
