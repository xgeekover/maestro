import { contextBridge } from 'electron'

// 렌더러에 노출할 안전한 브리지 (스캐폴딩). Phase 5에서 백엔드 API/IPC 래핑.
contextBridge.exposeInMainWorld('maestro', {
  version: '0.1.0',
})
