import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type {LoginResponse} from '@/lib/api'

interface AuthState {
  token: string | null
  userId: string | null
  name: string | null
  role: string | null

  setAuth: (authData: LoginResponse) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      userId: null,
      name: null,
      role: null,

      setAuth: (authData) =>
        set(() => ({
          token: authData.token ,
          userId: authData.userId ,
          name: authData.name,
          role: authData.role,
        })),

      logout: () =>
        set(() => ({
          token: null,
          userId: null,
          name: null,
          role: null,
        })),
    }),
    {
      name: 'ember-auth-storage',
    }
  )
)