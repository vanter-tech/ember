import { create } from 'zustand'

interface AuthState {
  token: string | null
  setToken: (newToken: string | null) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  token: localStorage.getItem('token'),
  setToken: (newToken) => {
    if (newToken) {
      localStorage.setItem('token', newToken)
    }
    set({ token: newToken })
  },
  logout: () => {
    localStorage.removeItem('token')
    set({ token: null })
  },
}))
