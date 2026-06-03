package pl.devstyle.aj.footprint.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pl.devstyle.aj.core.BaseEntity;
import pl.devstyle.aj.footprint.api.Normalisation;
import pl.devstyle.aj.footprint.api.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "footprint_audit_log")
// Intentionally re-declares the "base_seq" generator name inherited from BaseEntity. Each entity
// in this codebase supplies its own sequence with the same logical name (precedent: Product). If
// BaseEntity ever renames its generator, this annotation must follow.
@SequenceGenerator(name = "base_seq", sequenceName = "footprint_audit_log_id_seq", allocationSize = 1)
@Getter
@Setter
@NoArgsConstructor
public class FootprintAuditEntity extends BaseEntity {

    @Column(nullable = false, unique = true)
    private UUID correlationId;

    @Column(name = "comparison_group_id")
    private UUID comparisonGroupId;

    @Column(nullable = false, length = 100)
    private String productId;

    @Column(name = "caller_id", length = 100)
    private String callerId;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "total_kg_co2", nullable = false, precision = 12, scale = 4)
    private BigDecimal totalKgCo2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Strictness strictness;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Normalisation normalisation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> breakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> warnings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "factor_versions", columnDefinition = "jsonb", nullable = false)
    private List<String> factorVersions;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FootprintAuditEntity other)) return false;
        if (correlationId == null) return super.equals(o);
        return Objects.equals(correlationId, other.correlationId);
    }

    @Override
    public int hashCode() {
        return correlationId == null ? super.hashCode() : Objects.hash(correlationId);
    }
}
