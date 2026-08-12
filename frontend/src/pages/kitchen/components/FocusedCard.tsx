import type { kitchenOrders } from '@/lib/api'
import { Button } from '@/components/ui/button'
import { Card, CardHeader } from '@/components/ui/card'
import { getColorForTable } from '@/components/AvatarInitials'
import { Clock, TicketCheck, UserCheck } from 'lucide-react'

export const FocusedCard = ({ order }: { order: kitchenOrders }) => {
  return (
    <>
      <div className="flex flex-col gap-6 shrink-0">
        <Card
          className={`p-5 rounded-3xl border-l-8 ${getColorForTable(order.sessionId!)}`}
        >
          <CardHeader className="flex flex-col gap-2 border-b">
            <h2 className="text-2xl font-bold text-[#8c1717] tracking-tight">
              Detalles de Orden - M{order.tableNumber}
            </h2>
            <div className="w-full flex items-center justify-between">
              <div className="flex gap-3">
                <span className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <TicketCheck /> Ticket: #
                  {order.id!.substring(0, 6).toUpperCase()}
                </span>
                <span className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <UserCheck /> Cliente: #-Por-iterar
                </span>
                <span className="flex items-center gap-2 text-xs text-gray-500 mt-1">
                  <Clock /> Ingreso: {order.createdAt}
                </span>
              </div>

              <div className="flex flex-row gap-3">
                <Button className="p-6 ">Imprimir</Button>
                <Button className="p-6 " variant={'destructive'}>
                  Anular
                </Button>
              </div>
            </div>
          </CardHeader>
        </Card>
      </div>
    </>
  )
}
