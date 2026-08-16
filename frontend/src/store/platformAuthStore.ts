import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { PlatformAuthResponse } from '@/lib/platformApi'

interface PlatformAuthState extends PlatformAuthResponse {
  setAuth: (data: PlatformAuthResponse) => void
  logout: () => void
}

export const usePlatformAuthStore = create<PlatformAuthState>()(
  persist(
    (set) => ({
      token: undefined,
      operatorId: undefined,
      name: undefined,
      email: undefined,

      setAuth: (data) => set(data),

      logout: () =>
        set({
          token: undefined,
          operatorId: undefined,
          name: undefined,
          email: undefined,
        }),
    }),
    {
      name: 'ember-platform-auth-storage',
    }
  )
)
