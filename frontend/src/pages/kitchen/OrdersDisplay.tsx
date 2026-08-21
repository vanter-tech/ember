import { kitchenServices } from '@/lib/api'
import { useQuery } from '@tanstack/react-query'
import { QueueCard } from './components/QueueCard'
import { FocusedCard } from './components/FocusedCard'
import { useWebsocketStore } from '@/store/websocket'
import { Badge } from '@/components/ui/badge'
import { LanguageSwitcher } from '@/components/LanguageSwitcher'
import { useTranslation } from '@/lib/i18n'

export const OrdersDisplays = () => {
  const isConnected = useWebsocketStore((state) => state.isConnected)
  const { t } = useTranslation('kitchen')

  const {
    data: info = [],
    isLoading,
    isError,
  } = useQuery({
    queryKey: ['kitchenOrders'],
    queryFn: () => kitchenServices.getOrdersByTables(),
  })

  const ordersRaw = info.flatMap((item) => item.orders)
  const orders = ordersRaw.sort(
    (a, b) =>
      new Date(a?.createdAt!).getTime() - new Date(b?.createdAt!).getTime()
  )

  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center h-full">
        <span className="text-gray-500">{t('loadingOrders')}</span>
      </div>
    )
  }

  if (isError) {
    return (
      <div className="flex flex-1 items-center justify-center h-full">
        <span className="text-red-600">
          {t('loadingOrdersError')}
        </span>
      </div>
    )
  }

  return (
    <div className="flex flex-col h-full p-2">
      <div className="flex items-center justify-center flex-col relative w-full h-20 shadow-sm rounded-3xl p-4">
        <Badge
          variant={isConnected ? 'default' : 'destructive'}
          className="absolute top-3 right-4"
        >
          <span
            className={`size-1.5 rounded-full ${isConnected ? 'bg-primary-foreground' : 'bg-destructive'}`}
          />
          {isConnected ? t('connected') : t('disconnected')}
        </Badge>
        <div className="flex items-center gap-3">
          <h1 className="text-3xl font-bold text-[#8c1717] tracking-tight">
            Ember
          </h1>
          <LanguageSwitcher />
        </div>
        <span className="text-sm text-gray-500 mt-1">
          {t('kdsSubtitle')}
        </span>
      </div>
      <div className="flex flex-1 items-start gap-6 overflow-x-auto p-6">
        {orders.map((item, index) => (
          <QueueCard key={item?.id ?? index} order={item!} />
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
