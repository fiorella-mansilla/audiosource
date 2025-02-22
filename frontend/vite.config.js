import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import eslint from 'vite-plugin-eslint';

export default defineConfig({
  assetsInclude: ['**/*.wav'],
  css: {
    preprocessorOptions: {
      scss: {
        api: 'modern-compiler',
        silenceDeprecations: ['import', 'global-builtin', 'color-functions', 'mixed-decls']
      }
    }
  },
  plugins: [
    react(), 
    eslint({
      fix: true, // Automatically fix ESLint issues
      include: ['src/**/*.js', 'src/**/*.jsx'], 
      exclude: ['node_modules', 'legacy'], // Exclude files or directories
    })
  ]
});