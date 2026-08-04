import { FloatingNav } from '@/components/FloatingNav'
import { Outlet } from 'react-router-dom'
import { useWebsocketStore } from '@/store/websocket'
import { useSessionStore } from '@/store/sessionStore'
import { useEffect } from 'react'

export const CustomerLayout = () => {

  const { connect, disconnect } = useWebsocketStore()
  const sessionId = useSessionStore((state) => state.id)

  useEffect(() => {
    if (sessionId) {
      connect()
    }

    return () => {
      disconnect()
    }
  }, [sessionId, connect])

  return (
    <div className="min-h-screen bg-zinc-50/50 relative pb-32 p-6">
      <main className="w-full">
        <Outlet />
      </main>
      <FloatingNav />
    </div>
  )
}
