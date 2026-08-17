import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { sessionResponse } from '@/lib/api'
import type { orderItemDTO } from '@/lib/api'
import type { participantDTO } from '@/lib/api'
import type { Bill, BillSplit } from '@/lib/api'

interface sessionState extends sessionResponse {
  bill?: Bill
  billSplits?: BillSplit[]
  setSession: (data: sessionResponse) => void
  clearSession: () => void
  updateSession: (data: any) => void
  addParticipant: (participant: participantDTO) => void
  addItem: (item: orderItemDTO) => void
  setBillReady: (bill: Bill, splits: BillSplit[]) => void
  markSplitPaid: (participantName: string) => void
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
      bill: undefined,
      billSplits: undefined,


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
      setBillReady: (bill, splits) => {
        set({ bill, billSplits: splits })
      },
      markSplitPaid: (participantName) => {
        set((state) => ({
          billSplits: (state.billSplits || []).map((split) =>
            split.participantName === participantName
              ? { ...split, paid: true }
              : split
          ),
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
          bill: undefined,
          billSplits: undefined,
        })
      },
    }),
    {
      name: 'ember-session-storage',
    }
  )
)
