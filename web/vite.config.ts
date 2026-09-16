import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  // Relative base so the build works on any GitHub Pages path
  // (user site, project site, or a renamed repo)
  base: './',
  plugins: [react()],
})
