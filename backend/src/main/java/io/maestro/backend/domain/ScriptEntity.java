package io.maestro.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 스크립트 영속 엔티티 (H2, ADR-0002 O-1). Phase 4는 JPA ddl-auto.
 * 소유권(owner_id)·Flyway 스키마는 Phase 5(인증/다중사용자)에서 정렬.
 */
@Entity
@Table(name = "script")
public class ScriptEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Lob
    @Column(nullable = false)
    private String source;

    @Column(name = "source_hash", nullable = false, length = 64)
    private String sourceHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ScriptEntity() {
    }

    public ScriptEntity(String id, String name, String source, String sourceHash,
                        Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.source = source;
        this.sourceHash = sourceHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSource() { return source; }
    public String getSourceHash() { return sourceHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String source, String sourceHash, Instant updatedAt) {
        this.name = name;
        this.source = source;
        this.sourceHash = sourceHash;
        this.updatedAt = updatedAt;
    }
}
