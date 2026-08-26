import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import toast from 'react-hot-toast'
import { inventoryService } from '@/lib/api'
import { useUIStore } from '@/store/uiStore'
import { useWebsocketStore } from '@/store/websocket'
import { useAuthStore } from '@/store/authStore'
import { Card, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Pencil } from 'lucide-react'
import { NewInventoryItemModal } from './components/NewInventoryItemModal'
import { EditInventoryItemModal } from './components/EditInventoryItemModal'
import { SectionTour } from '@/components/tours/SectionTour'
import { useTranslation } from '@/lib/i18n'

export const Inventory = () => {
  const { openModal } = useUIStore()
  const { t } = useTranslation('admin')

  const tourSteps = [
    {
      target: '#inventory-hub-sidebar',
      title: t('tourStockSidebarTitle'),
      content: t('tourStockSidebarContent'),
      skipBeacon: true,
    },
    {
      target: '#inventory-tour-grid',
      title: t('tourStockGridTitle'),
      content: t('tourStockGridContent'),
    },
    {
      target: '#topnav-create-button',
      title: t('tourStockCreateTitle'),
      content: t('tourStockCreateContent'),
    },
  ]
  const restaurantId = useAuthStore((state) => state.restaurantId)
  const {
    connect,
    disconnect,
    isConnected,
    stompClient,
    subscribeToInventory,
    unsubscribeFromInventory,
    lastLowStockAlert,
    clearLowStockAlert,
  } = useWebsocketStore()

  const { data: items = [], isLoading, isError } = useQuery({
    queryKey: ['inventoryItems'],
    queryFn: inventoryService.getAll,
  })

  useEffect(() => {
    if (restaurantId) {
      connect()
    }
    return () => {
      disconnect()
    }
  }, [restaurantId, connect, disconnect])

  useEffect(() => {
    if (restaurantId && isConnected && stompClient?.connected) {
      subscribeToInventory(restaurantId)
    }
    return () => unsubscribeFromInventory()
  }, [restaurantId, isConnected, stompClient, subscribeToInventory, unsubscribeFromInventory])

  useEffect(() => {
    if (!lastLowStockAlert) return
    toast(t('inventoryLowStockAlertToast', { name: lastLowStockAlert.menuItemName }))
    clearLowStockAlert()
  }, [lastLowStockAlert, clearLowStockAlert, t])

  if (isLoading) return <div className="p-6 text-zinc-500">{t('loadingInventory')}</div>
  if (isError) return <div className="p-6 text-red-500">{t('loadingInventoryError')}</div>

  return (
    <div>
      <div id="inventory-tour-grid" className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {items.map((item) => (
          <Card key={item.id} className="p-4 rounded-3xl flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <CardTitle>{item.menuItemName}</CardTitle>
              <Button variant="ghost" size="icon" onClick={() => openModal('EDIT_INVENTORY_ITEM', item)}>
                <Pencil className="h-4 w-4" />
              </Button>
            </div>
            <p className="text-sm text-zinc-500">
              {item.currentStock} {item.unit}
            </p>
            {(item.currentStock ?? 0) <= 0 && (
              <Badge variant="destructive" className="w-fit">{t('inventoryStockOutBadge')}</Badge>
            )}
            {(item.currentStock ?? 0) > 0 && (item.currentStock ?? 0) <= (item.lowStockThreshold ?? 0) && (
              <Badge className="w-fit bg-amber-500/10 text-amber-600">{t('inventoryLowStockBadge')}</Badge>
            )}
          </Card>
        ))}
      </div>
      <NewInventoryItemModal />
      <EditInventoryItemModal />
      <SectionTour sectionId="admin-inventory-stock" steps={tourSteps} />
    </div>
  )
}
