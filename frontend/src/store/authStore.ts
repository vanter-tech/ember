import {create} from 'zustand'
import { persist } from 'zustand/middleware'
import type {LoginResponse} from '@/lib/api'
import { useSessionStore } from './sessionStore'

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

      setAuth: (data) => set(data),

      logout: () =>
        set({
          token: undefined,
          userId: undefined,
          restaurantId: undefined,
          name: undefined,
          role: undefined,
        }), 
    }),
    {
      name: 'ember-auth-storage'
    }
  ))