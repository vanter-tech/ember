import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { sessionResponse } from '@/lib/api'

interface sessionState extends sessionResponse {
  setSession: (data: sessionResponse) => void
  clearSession: () => void
  updateSession: (data: any) => void
  addParticipant: (participant: any) => void

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
      updateSession: (data) =>({...data}),
      addParticipant: (participant) => {
        set((state) => ({
          participants: [...(state.participants || []), {userId: participant.userId, name: participant.userName}],
        }))
      },


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
