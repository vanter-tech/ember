import { FloatingNav } from '@/components/FloatingNav'
import { Outlet } from 'react-router-dom'

export const CustomerLayout = () => {
  return (
    <div className="min-h-screen bg-zinc-50/50 relative pb-32 p-6">
      <main className="w-full">
        <Outlet />
      </main>
      <FloatingNav />
    </div>
  )
}
