-- Maestro DB 스키마 (설계, Phase 1) — H2 서버모드 시작, Postgres 전환경로 유지 (ADR-0002 O-1)
-- 벤더 특화 SQL 회피, 표준 타입 위주. 구현 시 Flyway V1__init.sql 로 backend 모듈에 배치.
-- 메트릭은 DB에 저장하지 않고 인메모리 링버퍼로 관리(ADR-0001 D3). 여기엔 정의/상태/이력만.

-- ===== 인증/인가 (ADR-0002 O-8) =====
CREATE TABLE app_user (
    id            VARCHAR(36)  PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,            -- BCrypt
    display_name  VARCHAR(128),
    created_at    TIMESTAMP    NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE user_role (
    user_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
    role    VARCHAR(32) NOT NULL,                   -- ADMIN, AUTHOR, OPERATOR, VIEWER
    PRIMARY KEY (user_id, role)
);

-- ===== 스크립트 (FR-1/FR-2) =====
CREATE TABLE script (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    source      CLOB         NOT NULL,
    source_hash VARCHAR(64)  NOT NULL,              -- 컴파일 캐시 무효화 키
    owner_id    VARCHAR(36)  NOT NULL REFERENCES app_user(id),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);
CREATE INDEX ix_script_owner ON script(owner_id);

-- ===== 실행(러너 프로세스) 이력/상태 (FR-3/FR-4/FR-5) =====
CREATE TABLE run (
    id             VARCHAR(36) PRIMARY KEY,
    script_id      VARCHAR(36) NOT NULL REFERENCES script(id),
    flow_id        VARCHAR(36),                     -- 플로우 배포로 기동된 경우
    flow_node_id   VARCHAR(64),
    state          VARCHAR(16) NOT NULL,            -- COMPILING/STARTING/RUNNING/STOPPING/STOPPED/ERROR
    tick_period_ms BIGINT,
    tick_policy    VARCHAR(16) NOT NULL DEFAULT 'CONTINUE',
    max_heap_bytes BIGINT,
    pid            BIGINT,
    started_at     TIMESTAMP,
    ended_at       TIMESTAMP,
    restart_count  INT         NOT NULL DEFAULT 0,
    last_error     CLOB,
    owner_id       VARCHAR(36) NOT NULL REFERENCES app_user(id)
);
CREATE INDEX ix_run_state ON run(state);
CREATE INDEX ix_run_script ON run(script_id);

-- ===== 플로우 (FR-7) — DAG, 사이클 금지(O-9) =====
CREATE TABLE flow (
    id         VARCHAR(36)  PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    graph_json CLOB         NOT NULL,               -- nodes/edges 직렬화(위상정렬 라우팅)
    owner_id   VARCHAR(36)  NOT NULL REFERENCES app_user(id),
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

-- ===== 모듈 레지스트리 (FR-8) — semver(O-10) =====
CREATE TABLE module (
    id          VARCHAR(36)  PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    version     VARCHAR(32)  NOT NULL,              -- semver
    spec_json   CLOB         NOT NULL,              -- 입출력 포트/파라미터 스키마
    artifact    CLOB,                               -- 스크립트 소스 또는 서브플로우 정의
    created_at  TIMESTAMP    NOT NULL,
    UNIQUE (name, version)
);

-- ===== 스크립트 상태 저장소 (SDK KeyValueStore, O-6 재시작 복원) =====
CREATE TABLE script_state (
    script_id  VARCHAR(36) NOT NULL REFERENCES script(id),
    state_key  VARCHAR(255) NOT NULL,
    value_json CLOB,
    updated_at TIMESTAMP   NOT NULL,
    PRIMARY KEY (script_id, state_key)
);
