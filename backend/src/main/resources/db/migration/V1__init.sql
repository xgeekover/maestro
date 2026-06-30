-- Maestro V1 — 현재 구현 엔티티에 정합하는 스키마 (ScriptEntity·FlowEntity·ModuleEntity).
-- 인증/소유권(owner_id)·run 이력·script_state(KV)는 미구현 → 후속 마이그레이션(V2+)에서 추가.
-- 설계 전체 스키마는 docs/db/schema.sql 참조.

CREATE TABLE IF NOT EXISTS script (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    source      CLOB         NOT NULL,
    source_hash VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS flow (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    graph_json  CLOB         NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS module (
    id          VARCHAR(36)  NOT NULL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    version     VARCHAR(32)  NOT NULL,
    spec_json   CLOB         NOT NULL,
    source      CLOB         NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    CONSTRAINT uq_module_name_version UNIQUE (name, version)
);
