package io.maestro.backend.state;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 스크립트/플로우노드의 키-값 상태 1건. 재시작 간 유지되어 {@code ScriptContext.state()}가 복원한다.
 * owner_key = scriptId(단독) 또는 flowId:nodeId(플로우 노드).
 */
@Entity
@Table(name = "script_state")
@IdClass(ScriptStateId.class)
public class ScriptStateEntity {

    @Id
    @Column(name = "owner_key", length = 512)
    private String ownerKey;

    @Id
    @Column(name = "state_key", length = 512)
    private String stateKey;

    @Lob
    @Column(name = "value_json")
    private String valueJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScriptStateEntity() {
    }

    public ScriptStateEntity(String ownerKey, String stateKey) {
        this.ownerKey = ownerKey;
        this.stateKey = stateKey;
    }

    public String getOwnerKey() { return ownerKey; }
    public String getStateKey() { return stateKey; }
    public String getValueJson() { return valueJson; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setValueJson(String valueJson) { this.valueJson = valueJson; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
