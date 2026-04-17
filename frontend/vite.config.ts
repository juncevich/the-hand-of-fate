import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'path'
import { mockApiPlugin } from './mock-plugin'

export default defineConfig(({ mode }) => {
  const isMock = mode === 'mock'

  return {
    plugins: [
      react(),
      tailwindcss(),
      ...(isMock ? [mockApiPlugin()] : []),
    ],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
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
