import { FloatingNav } from '@/components/FloatingNav'
import { CashShiftSentinel } from '@/components/CashShiftSentinel'
import { TopNav } from '@/components/TopNav'
import { Outlet } from 'react-router-dom'
import { useWebsocketStore } from '@/store/websocket'
import { useAuthStore } from '@/store/authStore'
import { useQuickAccessStore } from '@/store/quickAccessStore'
import { SetPinPrompt } from '@/pages/auth/SetPinPrompt'
import { useTranslation } from '@/lib/i18n'
import { useEffect, useState } from 'react'

export const WaiterLayout = () => {
  const { connect, disconnect, isConnected, subscribeToWaiter, stompClient } =
    useWebsocketStore()
  const restaurantId = useAuthStore((state) => state.restaurantId)
  const role = useAuthStore((state) => state.role)
  const pinEmail = useQuickAccessStore(
    (s) => s.profiles.find((p) => p.role === role)?.email,
  )
  const { t: tAuth } = useTranslation('auth')
  const [showSetPin, setShowSetPin] = useState(false)

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
      {pinEmail && (
        <div className="flex justify-end -mt-3 mb-4">
          <button
            type="button"
            onClick={() => setShowSetPin(true)}
            className="text-xs text-zinc-500 hover:underline"
          >
            {tAuth('setPinMenuItem')}
          </button>
        </div>
      )}
      {showSetPin && pinEmail && (
        <SetPinPrompt email={pinEmail} onDone={() => setShowSetPin(false)} />
      )}
      <main className="w-full">
        <Outlet />
      </main>
      <FloatingNav />
      <CashShiftSentinel />
    </div>
  )
}
