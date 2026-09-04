import { create } from 'zustand'
import { persist } from 'zustand/middleware'

export interface QuickAccessProfile {
  email: string
  name: string
  role: string
  initials: string
  colorSeed: number
  lastUsedAt: number
}

interface QuickAccessState {
  profiles: QuickAccessProfile[]
  remember: (p: { email: string; name: string; role: string }) => void
  forget: (email: string) => void
  clear: () => void
}

const MAX_PROFILES = 6

const initials = (name: string) =>
  name.trim().split(/\s+/).slice(0, 2).map((w) => w[0]?.toUpperCase() ?? '').join('') || '?'

const colorSeed = (email: string) => {
  let h = 0
  for (let i = 0; i < email.length; i++) h = (h * 31 + email.charCodeAt(i)) >>> 0
  return h % 360
}

export const useQuickAccessStore = create<QuickAccessState>()(
  persist(
    (set) => ({
      profiles: [],

      remember: ({ email, name, role }) =>
        set((state) => {
          const key = email.trim().toLowerCase()
          const next: QuickAccessProfile = {
            email,
            name,
            role,
            initials: initials(name),
            colorSeed: colorSeed(key),
            lastUsedAt: Date.now(),
          }
          const without = state.profiles.filter((p) => p.email.trim().toLowerCase() !== key)
          const merged = [next, ...without]
            .sort((a, b) => b.lastUsedAt - a.lastUsedAt)
            .slice(0, MAX_PROFILES)
          return { profiles: merged }
        }),

      forget: (email) =>
        set((state) => ({
          profiles: state.profiles.filter(
            (p) => p.email.trim().toLowerCase() !== email.trim().toLowerCase()),
        })),

      clear: () => set({ profiles: [] }),
    }),
    { name: 'ember-quick-access' }
  )
)
