package pl.devstyle.aj.footprint.audit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FootprintAuditRepository extends JpaRepository<FootprintAuditEntity, Long> {
    Optional<FootprintAuditEntity> findByCorrelationId(UUID correlationId);
}
