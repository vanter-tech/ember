import { kitchenServices } from '@/lib/api'
import { useQuery } from '@tanstack/react-query'
import { QueueCard } from './components/QueueCard'
import { FocusedCard } from './components/FocusedCard'

export const OrdersDisplays = () => {
  const { data: info = [] } = useQuery({
    queryKey: ['kitchenOrders'],
    queryFn: () => kitchenServices.getOrdersByTables(),
  })

  const ordersRaw = info.flatMap((item) => item.orders)
  const orders = ordersRaw.sort(
    (a, b) =>
      new Date(a?.createdAt!).getTime() - new Date(b?.createdAt!).getTime()
  )

  return (
    <div className="flex flex-col h-full p-2">
      <div className="flex items-center justify-center flex-col w-full h-20 shadow-sm rounded-3xl p-4">
        <h1 className="text-3xl font-bold text-[#8c1717] tracking-tight">
          Ember
        </h1>
        <span className="text-sm text-gray-500 mt-1">
          Monitor de cocina - KDS
        </span>
      </div>
      <div className="flex flex-1 items-start gap-6 overflow-x-auto p-6">
        {orders.map((item, index) => (
          <QueueCard key={index} order={item!} />
        ))}
      </div>
      {orders.length > 0 && (
        <div className="w-full px-6 pb-6">
          <FocusedCard order={orders[0]!} />
        </div>
      )}
    </div>
  )
}
