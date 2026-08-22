import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { Locale } from '@/locales/types'

interface LocaleState {
  locale: Locale
  setLocale: (locale: Locale) => void
}

export const useLocaleStore = create<LocaleState>()(
  persist(
    (set) => ({
      locale: 'es',
      setLocale: (locale) => set({ locale }),
    }),
    {
      name: 'ember-locale-storage',
    }
  )
)
