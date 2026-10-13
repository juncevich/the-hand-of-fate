import { create } from 'zustand'
import { queryClient } from '@/lib/queryClient'

interface AuthState {
  sessionVersion: number
  accessToken: string | null
  userId: string | null
  email: string | null
  displayName: string | null
  avatarUrl: string | null
  isAuthenticated: boolean
  setAuth: (data: {
    accessToken: string; userId: string; email: string; displayName: string; avatarUrl?: string | null
  }) => void
  updateAccessToken: (token: string) => void
  setAvatarUrl: (url: string | null) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>((set, get) => ({
  sessionVersion: 0,
  accessToken: null,
  userId: null,
  email: null,
  displayName: null,
  avatarUrl: null,
  isAuthenticated: false,

  setAuth: ({ accessToken, userId, email, displayName, avatarUrl = null }) => {
    const changed = get().userId !== userId
    if (changed) queryClient.clear()
    set({ accessToken, userId, email, displayName, avatarUrl, isAuthenticated: true,
      sessionVersion: get().sessionVersion + (changed ? 1 : 0) })
  },

  updateAccessToken: (token) => set({ accessToken: token }),

  setAvatarUrl: (avatarUrl) => set({ avatarUrl }),

  clearAuth: () => {
    queryClient.clear()
    set({ accessToken: null, userId: null, email: null, displayName: null, avatarUrl: null, isAuthenticated: false,
      sessionVersion: get().sessionVersion + 1 })
  },
}))
