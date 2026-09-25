import { defineConfig } from 'eslint/config'
import tseslint from 'typescript-eslint'
import reactHooks from 'eslint-plugin-react-hooks'

export default defineConfig(
  { ignores: ['dist/**', 'node_modules/**'] },
  {
    files: ['**/*.ts', '**/*.tsx'],
    extends: [tseslint.configs.recommended, reactHooks.configs.flat['recommended-latest']],
  },
  {
    // Type-aware rules need type information; the project service picks up the
    // nearest tsconfig.json for each file under `src/**`.
    files: ['src/**/*.ts', 'src/**/*.tsx'],
    languageOptions: {
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      '@typescript-eslint/no-floating-promises': 'error',
      // React doesn't care whether a JSX event handler prop (onClick, etc.) returns a
      // promise — this repo's mutate()/navigate()-in-handler pattern is intentional
      // fire-and-forget, so only flag genuine void-context misuse elsewhere.
      '@typescript-eslint/no-misused-promises': ['error', { checksVoidReturn: { attributes: false } }],
      '@typescript-eslint/await-thenable': 'error',
    },
  },
)
