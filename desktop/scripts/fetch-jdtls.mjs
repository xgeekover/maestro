// Eclipse JDT Language Server 다운로드 (Phase 5 스파이크).
// 사용: pnpm fetch:jdtls
// 결과: desktop/vendor/jdt-language-server/ 에 압축 해제, plugins/launcher.jar 심볼릭 생성.
//
// 주의: 약 100MB+ 다운로드. JDK(java)가 PATH에 있어야 JDT LS가 구동된다.
// 풀 LSP 연동(monaco-languageclient)은 디스플레이 환경에서 검증한다.

import { createWriteStream, existsSync, mkdirSync, readdirSync, symlinkSync, rmSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawnSync } from 'node:child_process'

const __dirname = dirname(fileURLToPath(import.meta.url))
const VENDOR = join(__dirname, '..', 'vendor')
const HOME = join(VENDOR, 'jdt-language-server')
// 안정 마일스톤 URL (필요 시 최신으로 갱신)
const URL =
  'https://download.eclipse.org/jdtls/milestones/1.31.0/jdt-language-server-1.31.0-202401111522.tar.gz'

async function main() {
  if (existsSync(join(HOME, 'plugins'))) {
    console.log('JDT LS 이미 설치됨:', HOME)
    linkLauncher()
    return
  }
  mkdirSync(HOME, { recursive: true })
  const tar = join(VENDOR, 'jdtls.tar.gz')
  console.log('다운로드 중:', URL)
  const res = await fetch(URL)
  if (!res.ok) {
    throw new Error(`다운로드 실패: ${res.status} ${res.statusText}`)
  }
  const file = createWriteStream(tar)
  await new Promise((resolve, reject) => {
    res.body.pipe ? res.body.pipe(file) : streamWeb(res, file).then(resolve, reject)
    file.on('finish', resolve)
    file.on('error', reject)
  })
  console.log('압축 해제 중…')
  const r = spawnSync('tar', ['-xzf', tar, '-C', HOME], { stdio: 'inherit' })
  if (r.status !== 0) {
    throw new Error('tar 압축 해제 실패')
  }
  rmSync(tar, { force: true })
  linkLauncher()
  console.log('완료:', HOME)
}

function streamWeb(res, file) {
  const reader = res.body.getReader()
  return (async () => {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      file.write(Buffer.from(value))
    }
    file.end()
  })()
}

function linkLauncher() {
  const plugins = join(HOME, 'plugins')
  if (!existsSync(plugins)) return
  const launcher = readdirSync(plugins).find(
    (f) => f.startsWith('org.eclipse.equinox.launcher_') && f.endsWith('.jar'),
  )
  if (launcher) {
    const link = join(plugins, 'launcher.jar')
    try {
      rmSync(link, { force: true })
      symlinkSync(launcher, link)
      console.log('launcher.jar →', launcher)
    } catch (e) {
      console.warn('launcher 심볼릭 실패(무시):', String(e))
    }
  }
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
