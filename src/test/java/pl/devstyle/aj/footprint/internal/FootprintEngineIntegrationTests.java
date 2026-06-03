package pl.devstyle.aj.footprint.internal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.archetype.pricing.ComponentId;
import pl.devstyle.aj.footprint.api.BreakdownNode;
import pl.devstyle.aj.footprint.api.CalculationOptions;
import pl.devstyle.aj.footprint.api.CompositeBreakdownNode;
import pl.devstyle.aj.footprint.api.FootprintFacade;
import pl.devstyle.aj.footprint.api.FootprintParameters;
import pl.devstyle.aj.footprint.api.LeafBreakdownNode;
import pl.devstyle.aj.footprint.audit.FootprintAuditRepository;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class FootprintEngineIntegrationTests {

    private static final String FV_RAW_MATERIAL_WINTER = "11111111-1111-1111-1111-000000000001";
    private static final String FV_RAW_MATERIAL_SUMMER = "11111111-1111-1111-1111-000000000002";

    @Autowired
    private FootprintFacade facade;

    @Autowired
    private FootprintAuditRepository auditRepository;

    @AfterEach
    void cleanAuditRows() {
        auditRepository.deleteAll();
    }

    @Test
    void calculateTotal_summerContext_returnsSummerFactorBreakdown() {
        var params = new FootprintParameters(
                "OFB-330",
                null, null, null, null,
                0,
                true,
                Instant.parse("2026-07-15T00:00:00Z")
        );

        var breakdown = facade.calculateTotal(params, CalculationOptions.defaults());

        var rawMaterialLeaf = findLeaf(breakdown.root(), "raw-material");
        assertThat(rawMaterialLeaf).isNotNull();
        assertThat(rawMaterialLeaf.factorVersionId()).isEqualTo(FV_RAW_MATERIAL_SUMMER);
        assertThat(rawMaterialLeaf.factorRate()).isEqualByComparingTo("0.95");
    }

    @Test
    void calculateTotal_winterContext_returnsWinterFactorBreakdown() {
        var params = new FootprintParameters(
                "OFB-330",
                null, null, null, null,
                0,
                true,
                Instant.parse("2026-03-15T00:00:00Z")
        );

        var breakdown = facade.calculateTotal(params, CalculationOptions.defaults());

        var rawMaterialLeaf = findLeaf(breakdown.root(), "raw-material");
        assertThat(rawMaterialLeaf).isNotNull();
        assertThat(rawMaterialLeaf.factorVersionId()).isEqualTo(FV_RAW_MATERIAL_WINTER);
        assertThat(rawMaterialLeaf.factorRate()).isEqualByComparingTo("0.90");
    }

    @Test
    void calculateTotal_nonRefrigeratedProduct_excludesColdStorageSubtree() {
        var params = new FootprintParameters(
                "CAW-042",
                null, null, null, null,
                0,
                false,
                Instant.parse("2026-03-15T00:00:00Z")
        );

        var breakdown = facade.calculateTotal(params, CalculationOptions.defaults());

        var root = (CompositeBreakdownNode) breakdown.root();
        assertThat(root.children())
                .noneMatch(c -> "cold-storage".equals(c.componentId().value()));
    }

    // Scenario I (audit-row identity for calculateUnit) is covered by
    // FootprintCalculateUnitAuditIntegrationTests — that class must NOT be @Transactional
    // because the AFTER_COMMIT listener never fires inside a rolled-back test transaction.

    private LeafBreakdownNode findLeaf(BreakdownNode node, String componentIdValue) {
        ComponentId target = new ComponentId(componentIdValue);
        if (node instanceof LeafBreakdownNode leaf && leaf.componentId().equals(target)) {
            return leaf;
        }
        if (node instanceof CompositeBreakdownNode composite) {
            for (var child : composite.children()) {
                var found = findLeaf(child, componentIdValue);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
