import { type ChildProcess, spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import path from 'node:path'

/**
 * Eclipse JDT Language Server 기동 모듈 (Phase 5 스파이크).
 *
 * <p>JDT LS는 별도 자바 프로세스로 실행되며 LSP(stdio)로 통신한다. SDK jar를 워크스페이스
 * 클래스패스로 제공하면 스크립트의 onStart/onTick/onEnd·ScriptContext 자동완성·진단이 가능하다.</p>
 *
 * <p><b>활성화 조건</b>: {@code pnpm fetch:jdtls}로 JDT LS를 내려받고, JDK(java)가 PATH에 있어야 한다.
 * 미설치 시 {@link isAvailable}가 false → 렌더러는 경량 SDK 보조(javaAssist)로 폴백한다.
 * 풀 시맨틱(monaco-languageclient 연동) 검증은 디스플레이 환경에서 수행한다.</p>
 */
export interface JdtlsHandle {
  process: ChildProcess
  stop: () => void
}

const JDTLS_HOME = path.join(__dirname, '..', '..', 'vendor', 'jdt-language-server')

export function jdtlsHome(): string {
  return process.env.MAESTRO_JDTLS_HOME ?? JDTLS_HOME
}

/** SDK jar 경로(러너/JDT LS 클래스패스 제공용). 빌드 산출물 위치는 배포 시 패키징(Phase 10). */
export function sdkClasspath(): string {
  return process.env.MAESTRO_SDK_CLASSPATH ?? path.join(__dirname, '..', '..', 'vendor', 'sdk.jar')
}

export function isAvailable(): boolean {
  const launcher = findLauncher()
  return launcher !== null
}

function findLauncher(): string | null {
  const pluginsDir = path.join(jdtlsHome(), 'plugins')
  if (!existsSync(pluginsDir)) {
    return null
  }
  // org.eclipse.equinox.launcher_*.jar
  // (실제 파일명은 버전에 따라 다르므로 fetch 스크립트가 심볼릭/고정 이름 제공 권장)
  const fixed = path.join(pluginsDir, 'launcher.jar')
  return existsSync(fixed) ? fixed : null
}

/**
 * JDT LS를 stdio 모드로 기동한다. 반환된 process의 stdin/stdout으로 LSP 메시지를 교환한다
 * (렌더러의 monaco-languageclient와 IPC 브리지로 연결 — 스파이크 연속 작업).
 */
export function startJdtls(workspaceDir: string): JdtlsHandle | null {
  const launcher = findLauncher()
  if (launcher === null) {
    return null
  }
  const configDir = path.join(jdtlsHome(), configForPlatform())
  const proc = spawn(
    'java',
    [
      '-Declipse.application=org.eclipse.jdt.ls.core.id1',
      '-Dosgi.bundles.defaultStartLevel=4',
      '-Declipse.product=org.eclipse.jdt.ls.core.product',
      '-Dlog.level=ALL',
      '-Xmx512m',
      '-jar',
      launcher,
      '-configuration',
      configDir,
      '-data',
      workspaceDir,
    ],
    { stdio: ['pipe', 'pipe', 'pipe'] },
  )
  return {
    process: proc,
    stop: () => proc.kill(),
  }
}

function configForPlatform(): string {
  switch (process.platform) {
    case 'win32':
      return 'config_win'
    case 'darwin':
      return 'config_mac'
    default:
      return 'config_linux'
  }
}
