import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'
import { mockApiPlugin } from './mock-plugin'

export default defineConfig(({ mode }) => {
  const isMock = mode === 'mock'

  return {
    plugins: [
      react(),
      tailwindcss(),
      ...(isMock ? [mockApiPlugin()] : []),
    ],
    build: {
      rolldownOptions: {
        // The silent-renew page loaded in a hidden iframe (src/auth/keycloak.ts)
        input: {
          main: path.resolve(import.meta.dirname, 'index.html'),
          silent: path.resolve(import.meta.dirname, 'oidc/silent.html'),
        },
      },
    },
    resolve: {
      alias: {
        '@': path.resolve(import.meta.dirname, './src'),
      },
    },
    server: {
      port: 3000,
      ...(isMock
        ? {}
        : {
            proxy: {
              '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
              },
              '/actuator': {
                target: 'http://localhost:8080',
                changeOrigin: true,
              },
              // Keycloak (docker-compose.infra.yml) on the SPA's origin, like the gateway's /auth/
              '/auth': {
                target: 'http://localhost:8180',
                xfwd: true,
              },
            },
          }),
    },
    test: {
      environment: 'jsdom',
      setupFiles: ['./src/test-setup.ts'],
      globals: true,
    },
  }
})
