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
  updateSession: (data: Partial<sessionResponse>) => void
  addParticipant: (participant: participantDTO) => void
  removeParticipant: (userId: string) => void
  addItem: (item: orderItemDTO) => void
  setBillReady: (bill: Bill, splits: BillSplit[]) => void
  markSplitStatus: (participantName: string, status: BillSplit['status']) => void
  replaceSplits: (splits: BillSplit[]) => void
  clearBill: () => void
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
      removeParticipant: (userId) => {
        set((state) => ({
          participants: (state.participants || []).filter((p) => p.userId !== userId),
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
      markSplitStatus: (participantName, status) => {
        set((state) => ({
          billSplits: (state.billSplits || []).map((split) =>
            split.participantName === participantName
              ? { ...split, status }
              : split
          ),
        }))
      },
      replaceSplits: (splits) => {
        set({ billSplits: splits })
      },
      clearBill: () => {
        set({ bill: undefined, billSplits: undefined })
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
