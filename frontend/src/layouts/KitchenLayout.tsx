import { FloatingNav } from '@/components/FloatingNav'
import {Outlet} from 'react-router-dom'

export const KitchenLayout = () => {
  return (
    <div className="min-h-screen bg-zinc-50/50 relative pb-32 p-6 flex flex-col">
      <main className="w-full flex flex-1 flex-col min-h-0">
          <Outlet/>
      </main>
      <FloatingNav />
    </div>
  )
}