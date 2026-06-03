package pl.devstyle.aj.footprint.audit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.footprint.api.CalculationOptions;
import pl.devstyle.aj.footprint.api.FootprintBreakdown;
import pl.devstyle.aj.footprint.api.FootprintFacade;
import pl.devstyle.aj.footprint.api.FootprintParameters;
import pl.devstyle.aj.footprint.api.Normalisation;
import pl.devstyle.aj.footprint.api.Strictness;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scenario I — calculateUnit writes exactly ONE audit row whose breakdown is the canonical
 * TOTAL view (not the per-100g scaled view).
 *
 * This class is intentionally NOT @Transactional. The audit listener uses
 * @TransactionalEventListener(AFTER_COMMIT) which never fires when the surrounding test
 * transaction rolls back at the end of the test method. Cleanup is manual via @AfterEach.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class FootprintCalculateUnitAuditIntegrationTests {

    @Autowired
    private FootprintFacade facade;

    @Autowired
    private FootprintAuditRepository auditRepository;

    @BeforeEach
    void startClean() {
        auditRepository.deleteAll();
    }

    @AfterEach
    void cleanAuditRows() {
        auditRepository.deleteAll();
    }

    @Test
    void calculateUnit_per100gOnHalfKgRefrigerated_writesSingleAuditRowWithTotalBreakdown() throws InterruptedException {
        UUID correlationId = UUID.randomUUID();
        var params = new FootprintParameters(
                "OFB-330",
                new BigDecimal("0.500"), null, null, null,
                14, true,
                Instant.parse("2026-07-15T00:00:00Z")
        );
        var options = new CalculationOptions(
                Strictness.STRICT, Normalisation.PER_100G, false,
                correlationId, null, "test"
        );

        FootprintBreakdown unitView = facade.calculateUnit(params, options);
        // Also call calculateTotal with a different correlationId so we can compare what
        // the canonical TOTAL value is for the same inputs.
        var totalOnlyOptions = new CalculationOptions(
                Strictness.STRICT, Normalisation.TOTAL, true,  // dryRun=true: no second audit row
                UUID.randomUUID(), null, "test"
        );
        FootprintBreakdown totalReference = facade.calculateTotal(params, totalOnlyOptions);

        waitForRowCount(1L);
        var row = auditRepository.findByCorrelationId(correlationId).orElseThrow();

        // The audit row's total_kg_co2 must be the canonical TOTAL view (not the per-100g scaled view).
        assertThat(row.getTotalKgCo2())
                .as("audit row must hold canonical TOTAL")
                .isEqualByComparingTo(totalReference.root().kgCo2());
        // Sanity: the per-100g caller view is strictly smaller than TOTAL for a 500g product.
        assertThat(unitView.root().kgCo2())
                .as("per-100g view must be smaller than canonical TOTAL")
                .isLessThan(row.getTotalKgCo2());
        // Exactly one row for this correlationId (unique constraint enforces this in DB, the
        // assertion documents the spec invariant: calculateUnit does NOT publish a second event).
        assertThat(auditRepository.count()).isEqualTo(1L);
    }

    private void waitForRowCount(long target) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (auditRepository.count() >= target) return;
            Thread.sleep(50L);
        }
        assertThat(auditRepository.count()).isEqualTo(target);
    }
}
