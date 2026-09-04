import { FloatingNav } from '@/components/FloatingNav'
import { TopNav } from '@/components/TopNav'
import { Outlet } from 'react-router-dom'
import { useOnboardingGate } from '@/hooks/useOnboardingGate'
import { AdminOnboardingWizard } from '@/components/onboarding/AdminOnboardingWizard'

export const AdminLayout = () => {
  const { needsOnboarding, isLoading } = useOnboardingGate()

  if (isLoading) {
    return null
  }

  if (needsOnboarding) {
    return <AdminOnboardingWizard />
  }

  return (
    <div className="min-h-screen bg-zinc-50/50 relative pb-32 p-6">
      <TopNav />
      <main className="w-full">
          <Outlet/>
      </main>
      <FloatingNav />
    </div>
  )
}
