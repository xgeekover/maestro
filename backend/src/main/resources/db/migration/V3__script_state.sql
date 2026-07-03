-- 스크립트/플로우노드 KV 상태 (재시작 간 영속). ScriptContext.state() 백엔드 저장소.
-- owner_key = scriptId(단독) 또는 flowId:nodeId(플로우 노드).

CREATE TABLE IF NOT EXISTS script_state (
    owner_key   VARCHAR(512) NOT NULL,
    state_key   VARCHAR(512) NOT NULL,
    value_json  CLOB,
    updated_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (owner_key, state_key)
);
