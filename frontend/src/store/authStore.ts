import { create } from 'zustand'

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  userId: string | null
  email: string | null
  displayName: string | null
  isAuthenticated: boolean
  setAuth: (data: { accessToken: string; refreshToken: string; userId: string; email: string; displayName: string }) => void
  updateAccessToken: (token: string) => void
  clearAuth: () => void
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  refreshToken: null,
  userId: null,
  email: null,
  displayName: null,
  isAuthenticated: false,

  setAuth: ({ accessToken, refreshToken, userId, email, displayName }) =>
    set({ accessToken, refreshToken, userId, email, displayName, isAuthenticated: true }),

  updateAccessToken: (token) => set({ accessToken: token }),

  clearAuth: () =>
    set({ accessToken: null, refreshToken: null, userId: null, email: null, displayName: null, isAuthenticated: false }),
}))
