import { FloatingNav } from '@/components/FloatingNav'
import { CashShiftSentinel } from '@/components/CashShiftSentinel'
import { TopNav } from '@/components/TopNav'
import { AnimatedOutlet } from '@/components/AnimatedOutlet'
import { CashDrawerWatcher } from '@/pages/accountant/cashDrawer/CashDrawerWatcher'

export const AccountantLayout = () => {
  return (
    <div className="min-h-screen bg-zinc-50/50 relative pb-32 p-6">
      <TopNav />
      <main className="w-full">
        <AnimatedOutlet />
      </main>
      <FloatingNav />
      <CashShiftSentinel />
      <CashDrawerWatcher />
    </div>
  )
}
