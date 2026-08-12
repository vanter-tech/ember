import {
  Card,
  CardHeader,
  CardTitle,
  CardContent,
  CardFooter,
} from '@/components/ui/card'
import { getColorForTable } from '@/components/AvatarInitials'
import type { kitchenOrders } from '@/lib/api'
import { Button } from '@/components/ui/button'

export const QueueCard = ({order}: {order: kitchenOrders}) => {

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
                {order.items?.map((item) => (
                    <li key={item.itemId} className='flex justify-start item-start gap-3'>
                        <span className='text-[#8c1717] font-bold'>
                            1X
                        </span>
                        <div className='flex flex-col'>
                            <span className='text-sm font-semibold text-gray-800'>
                                {item.name}
                            </span>
                        </div>
                    </li>
                ))}
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
