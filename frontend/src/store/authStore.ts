import { create } from 'zustand'
import { queryClient } from '@/lib/queryClient'

interface AuthState {
  sessionVersion: number
  accessToken: string | null
  userId: string | null
  email: string | null
  displayName: string | null
  isAuthenticated: boolean
  setAuth: (data: { accessToken: string; userId: string; email: string; displayName: string }) => void
  updateAccessToken: (token: string) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>((set, get) => ({
  sessionVersion: 0,
  accessToken: null,
  userId: null,
  email: null,
  displayName: null,
  isAuthenticated: false,

  setAuth: ({ accessToken, userId, email, displayName }) => {
    const changed = get().userId !== userId
    if (changed) queryClient.clear()
    set({ accessToken, userId, email, displayName, isAuthenticated: true,
      sessionVersion: get().sessionVersion + (changed ? 1 : 0) })
  },

  updateAccessToken: (token) => set({ accessToken: token }),

  clearAuth: () => {
    queryClient.clear()
    set({ accessToken: null, userId: null, email: null, displayName: null, isAuthenticated: false,
      sessionVersion: get().sessionVersion + 1 })
  },
}))
