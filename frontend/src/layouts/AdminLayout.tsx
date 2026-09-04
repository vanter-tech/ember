import { FloatingNav } from '@/components/FloatingNav'
import { TopNav } from '@/components/TopNav'
import { Outlet } from 'react-router-dom'
import { useOnboardingGate } from '@/hooks/useOnboardingGate'
import { AdminOnboardingWizard } from '@/components/onboarding/AdminOnboardingWizard'
import { useAuthStore } from '@/store/authStore'
import { useQuickAccessStore } from '@/store/quickAccessStore'
import { SetPinPrompt } from '@/pages/auth/SetPinPrompt'
import { useTranslation } from '@/lib/i18n'
import { useState } from 'react'

export const AdminLayout = () => {
  const { needsOnboarding, isLoading } = useOnboardingGate()
  const role = useAuthStore((state) => state.role)
  const pinEmail = useQuickAccessStore(
    (s) => s.profiles.find((p) => p.role === role)?.email,
  )
  const { t: tAuth } = useTranslation('auth')
  const [showSetPin, setShowSetPin] = useState(false)

  if (isLoading) {
    return null
  }

  if (needsOnboarding) {
    return <AdminOnboardingWizard />
  }

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
          <Outlet/>
      </main>
      <FloatingNav />
    </div>
  )
}
