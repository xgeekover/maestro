package io.maestro.backend.state;

import java.io.Serializable;
import java.util.Objects;

/** {@link ScriptStateEntity} 복합 키(owner_key + state_key). */
public class ScriptStateId implements Serializable {

    private String ownerKey;
    private String stateKey;

    public ScriptStateId() {
    }

    public ScriptStateId(String ownerKey, String stateKey) {
        this.ownerKey = ownerKey;
        this.stateKey = stateKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScriptStateId that)) {
            return false;
        }
        return Objects.equals(ownerKey, that.ownerKey) && Objects.equals(stateKey, that.stateKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ownerKey, stateKey);
    }
}
