import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import { loader } from '@monaco-editor/react'
import { registerJavaAssist } from './javaAssist'

// Vite로 번들된 monaco + editor worker 사용 (CDN 미사용 → 오프라인 Electron 호환).
self.MonacoEnvironment = {
  getWorker() {
    return new editorWorker()
  },
}

loader.config({ monaco })

// SDK API 기반 경량 자동완성/호버 (JDT LS 미연동 시에도 즉시 동작).
registerJavaAssist(monaco)

export { monaco }
