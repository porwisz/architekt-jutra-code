package pl.devstyle.aj.footprint.audit;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class FootprintAuditMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> LIST_OF_MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    FootprintAuditEntity toEntity(FootprintCalculatedEvent event) {
        var entity = new FootprintAuditEntity();
        entity.setCorrelationId(event.correlationId());
        entity.setComparisonGroupId(event.comparisonGroupId());
        entity.setProductId(event.productId());
        entity.setCallerId(event.callerId());
        entity.setRequestedAt(LocalDateTime.ofInstant(event.requestedAt(), ZoneOffset.UTC));
        entity.setTotalKgCo2(event.totalKgCo2());
        entity.setStrictness(event.strictness());
        entity.setNormalisation(event.normalisation());
        entity.setBreakdown(objectMapper.convertValue(event.breakdown(), MAP_TYPE));
        entity.setWarnings(objectMapper.convertValue(event.warnings(), LIST_OF_MAP_TYPE));
        entity.setFactorVersions(List.copyOf(event.factorVersions()));
        entity.setDryRun(event.dryRun());
        return entity;
    }
}
