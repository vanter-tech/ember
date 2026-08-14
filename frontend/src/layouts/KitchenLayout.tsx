import { FloatingNav } from '@/components/FloatingNav'
import {Outlet} from 'react-router-dom'
import { useWebsocketStore } from '@/store/websocket'
import { useAuthStore } from '@/store/authStore'
import { useEffect } from 'react'

export const KitchenLayout = () => {
  const { connect, disconnect, isConnected, subscribeToKitchen, stompClient } =
    useWebsocketStore()
  const restaurantId = useAuthStore((state) => state.restaurantId)

  useEffect(() => {
    if (restaurantId) {
      connect()
    }

    return () => {
      disconnect()
    }
  }, [restaurantId, connect])

  useEffect(() => {
    if (restaurantId && isConnected && stompClient?.connected) {
      subscribeToKitchen(restaurantId)
    }
  }, [isConnected, restaurantId, subscribeToKitchen, stompClient])

  return (
    <div className="min-h-screen bg-zinc-50/50 relative pb-32 p-6 flex flex-col">
      <main className="w-full flex flex-1 flex-col min-h-0">
          <Outlet/>
      </main>
      <FloatingNav />
    </div>
  )
}