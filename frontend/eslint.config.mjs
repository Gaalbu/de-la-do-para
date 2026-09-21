// @ts-check
import angular from 'angular-eslint';
import tseslint from 'typescript-eslint';

export default tseslint.config(
  {
    // Gerado de contracts/ via contracts:generate: nunca editado nem
    // auditado como código manual; a garantia é regenerar do zero.
    ignores: [
      'dist/**',
      'coverage/**',
      '.angular/**',
      'node_modules/**',
      'src/generated/**',
      'api-reference/**',
      'test-results/**',
      'playwright-report/**',
    ],
  },
  {
    files: ['**/*.ts'],
    extends: [...tseslint.configs.recommended, ...angular.configs.tsRecommended],
    rules: {
      // Nenhum `any` como atalho (C10): nem para fazer build passar.
      '@typescript-eslint/no-explicit-any': 'error',
    },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
  },
);
