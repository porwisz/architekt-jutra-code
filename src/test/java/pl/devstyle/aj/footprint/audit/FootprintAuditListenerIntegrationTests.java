package pl.devstyle.aj.footprint.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.archetype.pricing.ComponentId;
import pl.devstyle.aj.footprint.api.BreakdownNode;
import pl.devstyle.aj.footprint.api.FootprintBreakdown;
import pl.devstyle.aj.footprint.api.FootprintWarning;
import pl.devstyle.aj.footprint.api.LeafBreakdownNode;
import pl.devstyle.aj.footprint.api.Normalisation;
import pl.devstyle.aj.footprint.api.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class FootprintAuditListenerIntegrationTests {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private FootprintAuditRepository repository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanAuditRows() {
        // Listener uses REQUIRES_NEW so rows commit independently of the test transaction.
        // Without explicit cleanup they leak across test methods and classes.
        repository.deleteAll();
    }

    @Test
    void onFootprintCalculated_committedTransaction_persistsAuditRowWithinFiveSeconds() {
        var correlationId = UUID.randomUUID();
        var event = sampleEvent(correlationId, List.of(), false);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            Optional<FootprintAuditEntity> found = repository.findByCorrelationId(correlationId);
            assertThat(found).isPresent();
            assertThat(found.get().getProductId()).isEqualTo("prod-1");
            assertThat(found.get().getTotalKgCo2()).isEqualByComparingTo("1.2345");
            assertThat(found.get().isDryRun()).isFalse();
            assertThat(found.get().getStrictness()).isEqualTo(Strictness.STRICT);
            assertThat(found.get().getNormalisation()).isEqualTo(Normalisation.TOTAL);
            assertThat(found.get().getFactorVersions()).containsExactly("v-1");
        });
    }

    @Test
    void onFootprintCalculated_lenientMissingFactor_persistsAuditRowWithWarnings() {
        var correlationId = UUID.randomUUID();
        var warning = new FootprintWarning("MISSING_FACTOR", "no factor for last-mile",
                new ComponentId("last-mile"));
        var event = sampleEvent(correlationId, List.of(warning), false);

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            Optional<FootprintAuditEntity> found = repository.findByCorrelationId(correlationId);
            assertThat(found).isPresent();
            assertThat(found.get().getWarnings()).hasSize(1);
            assertThat(found.get().getWarnings().getFirst())
                    .containsEntry("code", "MISSING_FACTOR")
                    .containsEntry("message", "no factor for last-mile");
        });
    }

    // Scenario I (audit-row identity for calculateUnit) is covered by
    // FootprintCalculateUnitAuditIntegrationTests — see that non-@Transactional class.

    private FootprintCalculatedEvent sampleEvent(UUID correlationId,
                                                  List<FootprintWarning> warnings,
                                                  boolean dryRun) {
        BreakdownNode root = new LeafBreakdownNode(
                new ComponentId("material"),
                BigDecimal.valueOf(1.2345),
                3,
                "factor-a",
                BigDecimal.valueOf(1.2345),
                Instant.parse("2026-01-01T00:00:00Z"),
                BigDecimal.ONE,
                List.of()
        );
        var breakdown = new FootprintBreakdown(
                root,
                BigDecimal.valueOf(1.2345),
                List.of(),
                warnings,
                Instant.parse("2026-05-20T10:00:00Z"),
                correlationId
        );
        return new FootprintCalculatedEvent(
                correlationId,
                null,
                "prod-1",
                "anonymous",
                Instant.parse("2026-05-20T10:00:00Z"),
                BigDecimal.valueOf(1.2345),
                Strictness.STRICT,
                Normalisation.TOTAL,
                breakdown,
                warnings,
                List.of("v-1"),
                dryRun
        );
    }
}
