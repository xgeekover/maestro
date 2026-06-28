# Maestro — Phase 10 배포

> 상태: **구현·검증 완료** · 작성일 2026-06-29
> 모듈: `deploy/` · CI: `.github/workflows/`

백엔드 컨테이너화 + 데스크탑 설치본 + 릴리스 CI/CD + 설정/비밀 관리 + 문서.

## 백엔드 컨테이너 (`deploy/backend.Dockerfile`)
- **멀티스테이지**: 빌드(temurin-21-jdk) → `:backend:bootJar` + `:runner:installDist`.
- **런타임 = JDK**(JRE 아님): 러너가 `javax.tools.JavaCompiler`로 **동적 컴파일**하므로 JDK 필수.
- **러너 번들**: `runner/build/install/runner/lib`(runner+sdk+protocol+grpc)을 이미지에 포함, `MAESTRO_RUNNER_CLASSPATH=/app/runner-lib/*`로 백엔드가 러너 프로세스를 기동.
- **비루트** 사용자, **HEALTHCHECK**(`/actuator/health`), 포트 8080(REST/WS)·9090(gRPC).
- `deploy/docker-compose.yml` — 단일 호스트 기동. `deploy/.env.example` — 환경설정 템플릿.

### 검증 (실측)
```
docker build -f deploy/backend.Dockerfile -t maestro-backend . → 이미지 918MB
docker run → /actuator/health = {"status":"UP"}
컨테이너 내부 end-to-end: POST /api/scripts → POST /api/runs → status=RUNNING
  (백엔드가 컨테이너 내 러너 JVM을 번들 클래스패스로 기동 → gRPC 접속 → 동적 컴파일 → tick)
```
→ **프로덕션 이미지가 실제로 오케스트레이션(러너 기동·동적 컴파일·라이프사이클)을 수행함을 입증.**

## 데스크탑 설치본 (electron-builder)
- `desktop/package.json`의 `build` 설정: appId `io.maestro.desktop`, 타깃 **mac(dmg)·win(nsis)·linux(AppImage)**.
- 빌드: `pnpm dist`(= `vite build` + `tsc`(electron) + `electron-builder`).
- **코드 서명**: 인증서/시크릿 필요 → 후속(현재 미서명 설치본). macOS notarization·Windows 서명은 릴리스 시크릿 추가로 활성화.

## 릴리스 CI/CD (`.github/workflows/release.yml`)
`v*` 태그 푸시 트리거:
1. **backend-image** — Docker 빌드 → **GHCR**(`ghcr.io/<repo>/backend:<tag>`,`:latest`) 푸시.
2. **desktop-installers** — `macos/windows/ubuntu-latest` **매트릭스** → `electron-builder --publish never`로 설치본 빌드 → 아티팩트 업로드.
3. **github-release** — 아티팩트 수집 → GitHub Release 생성(릴리스 노트 자동).

CI(`ci.yml`): PR/푸시 시 JVM 빌드+테스트+JaCoCo 아티팩트, 데스크탑 typecheck+build.

## 설정 / 비밀 관리
- **환경변수 오버라이드**(Spring relaxed binding): `SERVER_PORT`, `MAESTRO_GRPC_PORT`, `MAESTRO_RUNNER_*`, `MAESTRO_RESTART_*`, `JAVA_OPTS`, `MAESTRO_BACKEND_URL`(데스크탑). 템플릿 `deploy/.env.example`.
- **비밀**: 러너에 비밀정보 비주입(NFR-3). DB 비밀번호 등은 시크릿 스토어/CI 시크릿으로 주입(`.env` 커밋 금지, `.gitignore` 처리).
- **다중 사용자/HA**: H2 → Postgres 전환(ADR-0002 O-1) 시 `SPRING_DATASOURCE_*` + `ddl-auto=validate` + Flyway로 전환.

## 한계 / 이월 (헤드리스·정직 고지)
- **데스크탑 설치본 실제 빌드/서명**은 각 OS 러너·인증서 필요 → 본 환경 미수행(구성·CI는 작성·검증). 실제 dmg/exe/AppImage 생성은 GitHub Actions 매트릭스에서 수행.
- **GHCR 푸시**는 레포·토큰 필요 → 태그 시 CI에서 수행.
- **인증(JWT/다중사용자)**: 설계·스키마는 준비(O-8), 구현은 후속(Phase 4에서 이월).

## Phase 10 게이트 체크리스트
- [x] 백엔드 Docker화(JDK·러너 번들·비루트·healthcheck) + compose + **이미지 빌드·구동·end-to-end 검증**
- [x] 데스크탑 설치본 설정(electron-builder, Win/Mac/Linux)
- [x] 릴리스 CI/CD(GHCR + 멀티OS 설치본 + GitHub Release)
- [x] 설정/비밀 관리(환경변수·`.env.example`·`.dockerignore`)
- [x] 문서(README·사용자 가이드·SDK 레퍼런스·배포)
