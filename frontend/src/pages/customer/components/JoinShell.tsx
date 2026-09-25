import type { ReactNode } from 'react'
import { AuthBackground } from '@/pages/auth/AuthBackground'
import { PoweredByVanter } from '@/pages/auth/PoweredByVanter'
import { LanguageFab } from '@/pages/auth/LanguageFab'

/** Login-style shell (landing backdrop, brand footer, language picker) for the customer join screens. */
export const JoinShell = ({ children }: { children: ReactNode }) => (
  <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-white p-4 pb-12">
    <AuthBackground />
    {children}
    <PoweredByVanter />
    <LanguageFab />
  </div>
)
