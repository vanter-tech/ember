import { FloatingNav } from '@/components/FloatingNav'
import { Outlet } from 'react-router-dom'
import { useWebsocketStore } from '@/store/websocket'
import { useSessionStore } from '@/store/sessionStore'
import { useEffect } from 'react'
import toast from 'react-hot-toast'
import { useTranslation } from '@/lib/i18n'

export const CustomerLayout = () => {

  const { t } = useTranslation('customer')
  const { connect, disconnect, lastBillRedistribution, clearBillRedistribution } =
    useWebsocketStore()
  const sessionId = useSessionStore((state) => state.id)

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
