import { create } from 'zustand'
import { persist } from 'zustand/middleware'

const seenKey = (sectionId: string, userId: string) => `${sectionId}:${userId}`

interface SectionTourState {
  seenByKey: Record<string, boolean>
  hasSeenTour: (sectionId: string, userId: string) => boolean
  markTourSeen: (sectionId: string, userId: string) => void
}

export const useSectionTourStore = create<SectionTourState>()(
  persist(
    (set, get) => ({
      seenByKey: {},
      hasSeenTour: (sectionId, userId) => Boolean(get().seenByKey[seenKey(sectionId, userId)]),
      markTourSeen: (sectionId, userId) =>
        set((state) => ({
          seenByKey: { ...state.seenByKey, [seenKey(sectionId, userId)]: true },
        })),
    }),
    {
      name: 'ember-section-tour-storage',
    }
  )
)
