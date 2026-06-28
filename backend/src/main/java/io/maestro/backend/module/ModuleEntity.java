package io.maestro.backend.module;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * 재사용 모듈 (FR-8): 입출력 포트·파라미터 스키마(spec) + 스크립트 소스(artifact)를
 * 이름·버전(semver)으로 패키징. 인스턴스화 시 독립 프로세스(플로우 노드).
 */
@Entity
@Table(name = "module", uniqueConstraints = @UniqueConstraint(columnNames = {"name", "version"}))
public class ModuleEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 32)
    private String version;   // semver

    @Lob
    @Column(name = "spec_json", nullable = false)
    private String specJson;  // 입출력 포트·파라미터 스키마

    @Lob
    @Column(nullable = false)
    private String source;    // 스크립트 소스(artifact)

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModuleEntity() {
    }

    public ModuleEntity(String id, String name, String version, String specJson, String source, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.specJson = specJson;
        this.source = source;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getVersion() { return version; }
    public String getSpecJson() { return specJson; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
}
