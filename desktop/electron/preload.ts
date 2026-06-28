import { contextBridge } from 'electron'

// 렌더러에 노출할 안전한 브리지. 백엔드 URL은 환경변수로 오버라이드 가능.
contextBridge.exposeInMainWorld('maestro', {
  version: '0.1.0',
  backendUrl: process.env.MAESTRO_BACKEND_URL ?? 'http://localhost:8080',
})
