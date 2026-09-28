import { FloatingNav } from '@/components/FloatingNav'
import { useLocation, useNavigate } from 'react-router-dom'
import { AnimatedOutlet } from '@/components/AnimatedOutlet'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useWebsocketStore } from '@/store/websocket'
import { useSessionStore } from '@/store/sessionStore'
import { SessionTableService } from '@/lib/api'
import { useEffect } from 'react'
import toast from 'react-hot-toast'
import { useTranslation } from '@/lib/i18n'
import { ParticipantsPopUp } from '@/pages/customer/components/ParticipantsPopUp'
import { ItemsFloatingIsland } from '@/pages/customer/components/ItemsFloatingIsland'
import { MobileActionsIsland } from '@/pages/customer/components/MobileActionsIsland'

export const CustomerLayout = () => {

  const { t } = useTranslation('customer')
  const {
    connect,
    disconnect,
    isConnected,
    stompClient,
    subscribeToSession,
    lastBillRedistribution,
    clearBillRedistribution,
  } = useWebsocketStore()
  const sessionId = useSessionStore((state) => state.id)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  // The circle/cart-preview belong to the ordering flow (menu, comanda, cuenta, recompensas) —
  // not the customer's home landing page, even while still seated at the table.
  const isHome = useLocation().pathname === '/customer/home'

  // The session topic (SPLIT_PAID / SESSION_CLOSED / cart frames) used to be subscribed only from
  // Menu.tsx, so a diner sitting on /comanda or /bill — or reloading there — never received it.
  // Living here it covers every customer route, guests included (they carry a CUSTOMER identity).
  useEffect(() => {
    if (sessionId && isConnected && stompClient?.connected) {
      subscribeToSession(sessionId)
    }
  }, [isConnected, sessionId, stompClient, subscribeToSession])

  // Server truth on every (re)load: the persisted store can outlive a session that was closed
  // while this tab had no live subscription.
  const {
    data: sessionData,
    isError: isSessionError,
    isLoading: isSessionLoading,
  } = useQuery({
    queryKey: ['sessionStatus', sessionId],
    queryFn: () => SessionTableService.sessionStatus(sessionId!),
    enabled: !!sessionId,
    retry: false,
  })

  useEffect(() => {
    if (isSessionLoading) return
    if (sessionId && (isSessionError || sessionData?.status === 'CLOSED')) {
      useSessionStore.getState().clearSession()
      queryClient.removeQueries({ queryKey: ['sessionStatus', sessionId] })
      navigate('/customer/home')
    }
  }, [sessionId, isSessionError, isSessionLoading, navigate, sessionData, queryClient])

  useEffect(() => {
    if (sessionId) {
      connect()
    }

    return () => {
      disconnect()
    }
  }, [sessionId, connect])

  // Announce to the diners still at the table that someone left mid-payment and their share was
  // spread across those present (SPLITS_REDISTRIBUTED on the session topic).
  useEffect(() => {
    if (!lastBillRedistribution) return
    toast(t('billSplitRedistributedToast', { name: lastBillRedistribution.departedParticipantName }))
    clearBillRedistribution()
  }, [lastBillRedistribution, clearBillRedistribution, t])

  return (
    <div className="min-h-screen bg-zinc-50/50 relative pb-32 p-6">
      <main className="w-full">
        <AnimatedOutlet />
      </main>
      {!isHome && (
        <>
          <div className="fixed bottom-24 md:bottom-10 left-11 z-50 hidden sm:block">
            <ParticipantsPopUp />
          </div>
          <div className="fixed bottom-24 md:bottom-10 right-11 z-50 hidden sm:block">
            <ItemsFloatingIsland />
          </div>
          <div className="fixed bottom-24 right-6 z-50 sm:hidden">
            <MobileActionsIsland />
          </div>
        </>
      )}
      <FloatingNav />
    </div>
  )
}
