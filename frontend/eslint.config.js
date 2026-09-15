import tsPlugin from '@typescript-eslint/eslint-plugin'
import tsParser from '@typescript-eslint/parser'
import reactHooksPlugin from 'eslint-plugin-react-hooks'

export default [
  { ignores: ['dist/**', 'node_modules/**'] },
  {
    files: ['**/*.ts', '**/*.tsx'],
    languageOptions: {
      parser: tsParser,
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
      'react-hooks': reactHooksPlugin,
    },
    rules: {
      ...tsPlugin.configs['recommended'].rules,
      ...reactHooksPlugin.configs['flat']['recommended-latest'].rules,
    },
  },
  {
    // Type-aware rules need a typed parser service — only `src/**` is covered
    // by tsconfig.json's `include`, so scope this to that subtree.
    files: ['src/**/*.ts', 'src/**/*.tsx'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        project: './tsconfig.json',
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
]
