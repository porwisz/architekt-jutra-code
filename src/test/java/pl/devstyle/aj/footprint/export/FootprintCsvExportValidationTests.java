package pl.devstyle.aj.footprint.export;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import pl.devstyle.aj.SecurityMockMvcConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.WithMockEditUser;
import pl.devstyle.aj.footprint.api.Normalisation;
import pl.devstyle.aj.footprint.api.Strictness;
import pl.devstyle.aj.footprint.audit.FootprintAuditEntity;
import pl.devstyle.aj.footprint.audit.FootprintAuditRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@WithMockEditUser
class FootprintCsvExportValidationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FootprintAuditRepository repository;

    @Test
    void exportCsv_unknownCorrelationId_returns404() throws Exception {
        mockMvc.perform(get("/api/footprints/calculations/{id}/export", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void exportCsv_formatNotCsv_returns400InvalidParameters() throws Exception {
        var correlationId = UUID.randomUUID();
        createAndSaveAuditRow(correlationId);

        mockMvc.perform(get("/api/footprints/calculations/{id}/export", correlationId)
                        .param("format", "json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETERS"))
                .andExpect(jsonPath("$.status").value(400));
    }

    private void createAndSaveAuditRow(UUID correlationId) {
        var entity = new FootprintAuditEntity();
        entity.setCorrelationId(correlationId);
        entity.setProductId("prod-1");
        entity.setRequestedAt(LocalDateTime.parse("2026-05-20T10:00:00"));
        entity.setTotalKgCo2(new BigDecimal("1.0000"));
        entity.setStrictness(Strictness.STRICT);
        entity.setNormalisation(Normalisation.TOTAL);

        Map<String, Object> leaf = new LinkedHashMap<>();
        leaf.put("componentId", Map.of("value", "raw-material"));
        leaf.put("kgCo2", new BigDecimal("1.0000"));
        leaf.put("scope", 3);
        leaf.put("factorVersionId", "f-1");
        leaf.put("factorRate", new BigDecimal("1.0000"));
        leaf.put("factorValidFrom", "2026-01-01T00:00:00Z");
        leaf.put("quantity", new BigDecimal("1.0000"));
        leaf.put("warnings", List.of());

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("componentId", Map.of("value", "product"));
        root.put("kgCo2", new BigDecimal("1.0000"));
        root.put("children", List.of(leaf));
        root.put("warnings", List.of());

        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("root", root);
        breakdown.put("total", new BigDecimal("1.0000"));
        breakdown.put("factorVersions", List.of("f-1"));
        breakdown.put("warnings", List.of());
        breakdown.put("computedAt", "2026-05-20T10:00:00Z");
        breakdown.put("correlationId", correlationId.toString());

        entity.setBreakdown(breakdown);
        entity.setWarnings(List.of());
        entity.setFactorVersions(List.of("f-1"));
        entity.setDryRun(false);
        repository.saveAndFlush(entity);
    }
}
