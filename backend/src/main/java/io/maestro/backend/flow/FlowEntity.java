package io.maestro.backend.flow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/** 플로우 영속 엔티티. 그래프(nodes/edges)는 JSON으로 직렬화 저장. */
@Entity
@Table(name = "flow")
public class FlowEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Lob
    @Column(name = "graph_json", nullable = false)
    private String graphJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FlowEntity() {
    }

    public FlowEntity(String id, String name, String graphJson, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.graphJson = graphJson;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getGraphJson() { return graphJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String graphJson, Instant updatedAt) {
        this.name = name;
        this.graphJson = graphJson;
        this.updatedAt = updatedAt;
    }
}
