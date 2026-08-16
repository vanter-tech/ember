import { FloatingNav } from '@/components/FloatingNav'
import { TopNav } from '@/components/TopNav'
import { Outlet } from 'react-router-dom'
import { useWebsocketStore } from '@/store/websocket'
import { useAuthStore } from '@/store/authStore'
import { useEffect } from 'react'

export const WaiterLayout = () => {
  const { connect, disconnect, isConnected, subscribeToWaiter, stompClient } =
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
      subscribeToWaiter(restaurantId)
    }
  }, [isConnected, restaurantId, subscribeToWaiter, stompClient])

  return (
    <div className="min-h-screen bg-zinc-50/50 relative pb-32 p-6">
      <TopNav />
      <main className="w-full">
        <Outlet />
      </main>
      <FloatingNav />
    </div>
  )
}
