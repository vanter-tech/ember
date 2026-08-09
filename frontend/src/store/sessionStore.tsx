import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { sessionResponse } from '@/lib/api'
import type { orderItemDTO } from '@/lib/api'
import type { participantDTO } from '@/lib/api'

interface sessionState extends sessionResponse {
  setSession: (data: sessionResponse) => void
  clearSession: () => void
  updateSession: (data: any) => void
  addParticipant: (participant: participantDTO) => void
  addItem: (item: orderItemDTO) => void


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
      items: undefined,


      setSession: (data) => set(data),
      updateSession: (data) => set(data),
      addParticipant: (participant) => {
        set((state) => ({
          participants: [...(state.participants || []), participant],
        }))
      },
      addItem: (item) => {
        set((state) => ({
          items: [...(state.items || []), item],
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
          items: undefined,
        })
      },
    }),
    {
      name: 'ember-session-storage',
    }
  )
)
