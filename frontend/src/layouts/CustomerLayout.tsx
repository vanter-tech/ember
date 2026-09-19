import { FloatingNav } from '@/components/FloatingNav'
import { Outlet, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useWebsocketStore } from '@/store/websocket'
import { useSessionStore } from '@/store/sessionStore'
import { SessionTableService } from '@/lib/api'
import { useEffect } from 'react'
import toast from 'react-hot-toast'
import { useTranslation } from '@/lib/i18n'

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
        <Outlet />
      </main>
      <FloatingNav />
    </div>
  )
}
