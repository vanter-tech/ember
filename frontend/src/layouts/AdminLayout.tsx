import { FloatingNav } from '@/components/FloatingNav'
import { TopNav } from '@/components/TopNav'
import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import { useOnboardingGate } from '@/hooks/useOnboardingGate'
import { AdminOnboardingWizard } from '@/components/onboarding/AdminOnboardingWizard'

export const AdminLayout = () => {
  const { needsOnboarding, isLoading } = useOnboardingGate()
  // Latch: the backend defaults totalTables to 10, so saving the business name flips
  // needsOnboarding to false mid-wizard; keep the wizard mounted until the user finishes it.
  const [wizardActive, setWizardActive] = useState(false)
  if (needsOnboarding && !wizardActive) {
    setWizardActive(true)
  }

  if (isLoading) {
    return null
  }

  if (needsOnboarding || wizardActive) {
    return <AdminOnboardingWizard onFinish={() => setWizardActive(false)} />
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
