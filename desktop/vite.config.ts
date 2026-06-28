import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Electron 렌더러를 위해 상대경로 base 사용 (file:// 로드 호환)
export default defineConfig({
  base: './',
  plugins: [react()],
  build: {
    outDir: 'dist/renderer',
    emptyOutDir: true,
  },
})
