package pl.devstyle.aj.footprint.export;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import pl.devstyle.aj.SecurityMockMvcConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.WithMockEditUser;
import pl.devstyle.aj.footprint.api.Normalisation;
import pl.devstyle.aj.footprint.api.Strictness;
import pl.devstyle.aj.footprint.audit.FootprintAuditEntity;
import pl.devstyle.aj.footprint.audit.FootprintAuditRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@WithMockEditUser
class FootprintCsvExportIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FootprintAuditRepository repository;

    @Test
    void exportCsv_existingRefrigeratedAuditRow_returnsHeaderPlusNineLeafRows() throws Exception {
        var correlationId = UUID.randomUUID();
        var leaves = refrigeratedLeaves();
        var total = sum(leaves);
        createAndSaveAuditRow(correlationId, "prod-refrigerated", total, refrigeratedBreakdown(leaves));

        MvcResult result = mockMvc.perform(get("/api/footprints/calculations/{id}/export", correlationId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment; filename=\"footprint-" + correlationId + ".csv\"")))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String[] lines = splitLines(body);
        assertThat(lines).hasSize(10); // header + 9 leaves
        assertThat(lines[0]).isEqualTo("correlation_id,computed_at,product_id,timestamp_param,"
                + "component_path,component_id,kg_co2,scope,factor_value,factor_valid_from,"
                + "factor_version_id,warnings");
    }

    @Test
    void exportCsv_existingNonRefrigeratedAuditRow_returnsHeaderPlusFourLeafRows() throws Exception {
        var correlationId = UUID.randomUUID();
        var leaves = nonRefrigeratedLeaves();
        var total = sum(leaves);
        createAndSaveAuditRow(correlationId, "prod-non-ref", total, nonRefrigeratedBreakdown(leaves));

        MvcResult result = mockMvc.perform(get("/api/footprints/calculations/{id}/export", correlationId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String[] lines = splitLines(body);
        assertThat(lines).hasSize(5); // header + 4 leaves
    }

    @Test
    void exportCsv_sumOfKgCo2_matchesAuditTotalWithinTolerance() throws Exception {
        var correlationId = UUID.randomUUID();
        var leaves = refrigeratedLeaves();
        var total = sum(leaves);
        var auditRow = createAndSaveAuditRow(correlationId, "prod-sum", total, refrigeratedBreakdown(leaves));

        MvcResult result = mockMvc.perform(get("/api/footprints/calculations/{id}/export", correlationId))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String[] lines = splitLines(body);
        BigDecimal computed = BigDecimal.ZERO;
        for (int i = 1; i < lines.length; i++) {
            // kg_co2 is column index 6
            String[] cols = lines[i].split(",", -1);
            computed = computed.add(new BigDecimal(cols[6]));
        }
        computed = computed.setScale(4, RoundingMode.HALF_UP);
        BigDecimal expected = auditRow.getTotalKgCo2().setScale(4, RoundingMode.HALF_UP);
        assertThat(computed).isEqualByComparingTo(expected);
    }

    // --- helpers ---

    private FootprintAuditEntity createAndSaveAuditRow(UUID correlationId,
                                                       String productId,
                                                       BigDecimal totalKgCo2,
                                                       Map<String, Object> breakdown) {
        var entity = new FootprintAuditEntity();
        entity.setCorrelationId(correlationId);
        entity.setProductId(productId);
        entity.setRequestedAt(LocalDateTime.parse("2026-05-20T10:00:00"));
        entity.setTotalKgCo2(totalKgCo2);
        entity.setStrictness(Strictness.STRICT);
        entity.setNormalisation(Normalisation.TOTAL);
        entity.setBreakdown(breakdown);
        entity.setWarnings(List.of());
        entity.setFactorVersions(List.of("v-1"));
        entity.setDryRun(false);
        return repository.saveAndFlush(entity);
    }

    private static Map<String, Object> leaf(String id, String kgCo2, int scope,
                                            String factorRate, String factorVersionId) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("componentId", Map.of("value", id));
        node.put("kgCo2", new BigDecimal(kgCo2));
        node.put("scope", scope);
        node.put("factorVersionId", factorVersionId);
        node.put("factorRate", new BigDecimal(factorRate));
        node.put("factorValidFrom", "2026-01-01T00:00:00Z");
        node.put("quantity", new BigDecimal("1.0000"));
        node.put("warnings", List.of());
        return node;
    }

    private static Map<String, Object> composite(String id, BigDecimal kgCo2, List<Map<String, Object>> children) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("componentId", Map.of("value", id));
        node.put("kgCo2", kgCo2);
        node.put("children", children);
        node.put("warnings", List.of());
        return node;
    }

    /** 9 leaves grouped under composites: materials(3) / production(2) / packaging(2) / cold-storage(2). */
    private static List<Map<String, Object>> refrigeratedLeaves() {
        return List.of(
                leaf("raw-material", "0.5000", 3, "0.5000", "f-1"),
                leaf("additives", "0.2500", 3, "0.2500", "f-2"),
                leaf("water", "0.1000", 3, "0.1000", "f-3"),
                leaf("energy", "0.3000", 2, "0.3000", "f-4"),
                leaf("labour", "0.0500", 1, "0.0500", "f-5"),
                leaf("primary-packaging", "0.1500", 3, "0.1500", "f-6"),
                leaf("secondary-packaging", "0.0750", 3, "0.0750", "f-7"),
                leaf("refrigeration-energy", "0.4000", 2, "0.4000", "f-8"),
                leaf("refrigerant-leakage", "0.0250", 1, "0.0250", "f-9")
        );
    }

    private static Map<String, Object> refrigeratedBreakdown(List<Map<String, Object>> leaves) {
        var materials = composite("materials", sumOf(leaves, 0, 3), leaves.subList(0, 3));
        var production = composite("production", sumOf(leaves, 3, 5), leaves.subList(3, 5));
        var packaging = composite("packaging", sumOf(leaves, 5, 7), leaves.subList(5, 7));
        var coldStorage = composite("cold-storage", sumOf(leaves, 7, 9), leaves.subList(7, 9));
        var root = composite("product", sum(leaves), List.of(materials, production, packaging, coldStorage));
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("root", root);
        wrapper.put("total", sum(leaves));
        wrapper.put("factorVersions", List.of("f-1", "f-2"));
        wrapper.put("warnings", List.of());
        wrapper.put("computedAt", "2026-05-20T10:00:00Z");
        wrapper.put("correlationId", UUID.randomUUID().toString());
        return wrapper;
    }

    private static List<Map<String, Object>> nonRefrigeratedLeaves() {
        return List.of(
                leaf("raw-material", "0.5000", 3, "0.5000", "f-1"),
                leaf("energy", "0.3000", 2, "0.3000", "f-4"),
                leaf("primary-packaging", "0.1500", 3, "0.1500", "f-6"),
                leaf("transport", "0.2000", 3, "0.2000", "f-10")
        );
    }

    private static Map<String, Object> nonRefrigeratedBreakdown(List<Map<String, Object>> leaves) {
        var materials = composite("materials", sumOf(leaves, 0, 1), leaves.subList(0, 1));
        var production = composite("production", sumOf(leaves, 1, 2), leaves.subList(1, 2));
        var packaging = composite("packaging", sumOf(leaves, 2, 3), leaves.subList(2, 3));
        var distribution = composite("distribution", sumOf(leaves, 3, 4), leaves.subList(3, 4));
        var root = composite("product", sum(leaves), List.of(materials, production, packaging, distribution));
        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("root", root);
        wrapper.put("total", sum(leaves));
        wrapper.put("factorVersions", List.of("f-1"));
        wrapper.put("warnings", List.of());
        wrapper.put("computedAt", "2026-05-20T10:00:00Z");
        wrapper.put("correlationId", UUID.randomUUID().toString());
        return wrapper;
    }

    private static BigDecimal sum(List<Map<String, Object>> leaves) {
        BigDecimal s = BigDecimal.ZERO;
        for (var leaf : leaves) {
            s = s.add((BigDecimal) leaf.get("kgCo2"));
        }
        return s.setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal sumOf(List<Map<String, Object>> leaves, int from, int to) {
        return sum(new ArrayList<>(leaves.subList(from, to)));
    }

    private static String[] splitLines(String body) {
        // commons-csv writes CRLF by default; tolerate either
        String normalized = body.replace("\r\n", "\n");
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.split("\n", -1);
    }

    // suppress unused warning - placeholder so future tests can reference MediaType
    @SuppressWarnings("unused")
    private static final MediaType TEXT_CSV = new MediaType("text", "csv");
}
