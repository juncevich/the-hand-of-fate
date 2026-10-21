import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { keycloakify } from 'keycloakify/vite-plugin'
import { appendFileSync } from 'node:fs'
import path from 'node:path'
import { mockApiPlugin } from './mock-plugin'
import { messagesRu } from './src/login/messages_ru'

export default defineConfig(({ mode }) => {
  const isMock = mode === 'mock'

  return {
    plugins: [
      react(),
      tailwindcss(),
      // Keycloak's login pages (src/login/) are built from this app: `npm run build-keycloak-theme`
      // packs them into a theme jar for Keycloak 26 (docs/plans/keycloak-auth.md, stage 6).
      keycloakify({
        themeName: 'fate',
        accountThemeImplementation: 'none',
        keycloakVersionTargets: { '22-to-25': false, 'all-other-versions': 'fate-keycloak-theme.jar' },
        // Keycloak renders some messages itself (field errors, the page message): give its bundle
        // the Russian texts it lacks. Runs in the theme's resources dir, before the jar is packed.
        postBuild: async () => {
          appendFileSync('theme/fate/login/messages/messages_ru.properties', toProperties(messagesRu))
        },
      }),
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

/** `key=value` lines of a Java properties file, non-ASCII escaped as Keycloak's own bundles are. */
function toProperties(messages: Record<string, string>): string {
  const escape = (text: string) =>
    text.replace(/[^\x20-\x7e]/g, (c) => `\\u${c.charCodeAt(0).toString(16).padStart(4, '0')}`)
  return `\n${Object.entries(messages)
    .map(([key, value]) => `${key}=${escape(value)}`)
    .join('\n')}\n`
}
