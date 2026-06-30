-- 완료된 실행 이력 (재시작 후에도 조회). RunHistoryEntity와 정합.

CREATE TABLE IF NOT EXISTS run_history (
    run_id        VARCHAR(36)  NOT NULL PRIMARY KEY,
    script_id     VARCHAR(255),
    script_name   VARCHAR(255),
    status        VARCHAR(16),
    pid           BIGINT,
    restart_count INT,
    started_at    TIMESTAMP,
    ended_at      TIMESTAMP,
    last_error    CLOB,
    flow_id       VARCHAR(255),
    node_id       VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS ix_run_history_ended ON run_history(ended_at);
