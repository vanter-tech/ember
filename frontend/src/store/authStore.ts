import {create} from 'zustand'
import { persist } from 'zustand/middleware'
import type {LoginResponse} from '@/lib/api'
import { queryClient } from '@/queryClient'

interface AuthState extends LoginResponse {
  setAuth: (data: LoginResponse) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: undefined,
      userId: undefined,
      restaurantId: undefined,
      name: undefined,
      role: undefined,

      // Clearing here (not just on logout) covers every identity transition that never calls
      // logout() first — e.g. logging in as a different tenant's admin in a tab that already had
      // another tenant's data cached. Without this, react-query's single app-wide `queryClient`
      // instantly serves the previous identity's stale cached data (isPending=false on mount).
      setAuth: (data) => {
        queryClient.clear()
        set(data)
      },

      logout: () => {
        queryClient.clear()
        set({
          token: undefined,
          userId: undefined,
          restaurantId: undefined,
          name: undefined,
          role: undefined,
        })
      },
    }),
    {
      name: 'ember-auth-storage'
    }
  ))