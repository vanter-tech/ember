import { useEffect, useState } from 'react'
import { kitchenServices, type OrderItemStatus } from '@/lib/api'
import { STATUS_LABEL } from './lib/itemStatus'
import { useQuery } from '@tanstack/react-query'
import { QueueCard } from './components/QueueCard'
import { FocusedCard } from './components/FocusedCard'
import { useWebsocketStore } from '@/store/websocket'
import { Badge } from '@/components/ui/badge'
import { LanguageSwitcher } from '@/components/LanguageSwitcher'
import { EmptyState } from '@/components/EmptyState'
import { ChefHat } from 'lucide-react'
import { useTranslation } from '@/lib/i18n'

const COLUMNS: OrderItemStatus[] = ['PENDING', 'PREPARING', 'READY']

export const OrdersDisplays = () => {
  const isConnected = useWebsocketStore((state) => state.isConnected)
  const { t } = useTranslation('kitchen')
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 30000)
    return () => clearInterval(id)
  }, [])

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
      new Date(a?.createdAt ?? 0).getTime() - new Date(b?.createdAt ?? 0).getTime()
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
      {orders.length === 0 ? (
        <div className="flex flex-1 w-full items-center justify-center p-6">
          <EmptyState icon={ChefHat} title={t('kdsEmptyTitle')} description={t('kdsEmptyDescription')} />
        </div>
      ) : (
        <div className="grid flex-1 min-h-0 grid-cols-1 gap-4 p-4 md:grid-cols-3">
          {COLUMNS.map((status) => {
            const cards = orders.filter((order) =>
              order?.items?.some((item) => (item.status ?? 'PENDING') === status)
            )
            return (
              <section key={status} className="flex min-h-0 flex-col rounded-2xl bg-gray-50 p-3">
                <header className="mb-3 flex items-center justify-between px-1">
                  <h2 className="text-lg font-bold text-gray-800">{STATUS_LABEL[status]}</h2>
                  <Badge variant="secondary">{cards.length}</Badge>
                </header>
                <div className="flex flex-1 flex-col gap-3 overflow-y-auto">
                  {cards.length === 0 ? (
                    <p className="py-6 text-center text-sm text-gray-400">{t('kdsColumnEmpty')}</p>
                  ) : (
                    cards.map((order, index) => (
                      <QueueCard key={order?.id ?? index} order={order!} status={status} now={now} />
                    ))
                  )}
                </div>
              </section>
            )
          })}
        </div>
      )}
      {orders.length > 0 && (
        <div className="w-full px-6 pb-6">
          <FocusedCard order={orders[0]!} />
        </div>
      )}
    </div>
  )
}
