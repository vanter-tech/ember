import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { sessionResponse } from '@/lib/api'

interface sessionState extends sessionResponse {
  setSession: (data: sessionResponse) => void
  clearSession: () => void
}

export const useSessionStore = create<sessionState>()(
  persist(
    (set) => ({
      id: undefined,
      tableId: undefined,
      waiterId: undefined,
      status: undefined,
      joinCode: undefined,
      participants: undefined,

      setSession: (data) => set(data),

      clearSession: () => {
        set({
          id: undefined,
          tableId: undefined,
          waiterId: undefined,
          status: undefined,
          joinCode: undefined,
          participants: undefined,
        })
      },
    }),
    {
      name: 'ember-session-storage',
    }
  )
)
