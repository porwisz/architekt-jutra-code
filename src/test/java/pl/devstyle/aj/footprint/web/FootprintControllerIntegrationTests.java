package pl.devstyle.aj.footprint.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.SecurityMockMvcConfiguration;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.WithMockEditUser;
import pl.devstyle.aj.footprint.audit.FootprintAuditRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@WithMockEditUser
class FootprintControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FootprintAuditRepository footprintAuditRepository;

    @AfterEach
    void cleanupAudit() {
        footprintAuditRepository.deleteAll();
    }

    @Test
    @Transactional
    void getFootprint_validRequest_returnsBreakdownAndCorrelationId() throws Exception {
        mockMvc.perform(get("/api/products/{productId}/footprint", "OFB-330")
                        .param("asOf", "2026-07-15T00:00:00Z")
                        .param("materialWeightKg", "0.5")
                        .param("storageDays", "14")
                        .param("requiresRefrigeration", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correlationId").value(notNullValue()))
                .andExpect(jsonPath("$.breakdown.componentId").value("product-footprint"))
                .andExpect(jsonPath("$.total").value(notNullValue()))
                .andExpect(jsonPath("$.unit").value("KG_CO2"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void getFootprint_dryRunTrue_returnsBreakdownAndNoAuditRow() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/products/{productId}/footprint", "OFB-330")
                        .param("asOf", "2026-07-15T00:00:00Z")
                        .param("materialWeightKg", "0.5")
                        .param("storageDays", "14")
                        .param("requiresRefrigeration", "true")
                        .param("dryRun", "true"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID correlationId = UUID.fromString(body.get("correlationId").asString());

        // Poll briefly to confirm no row is ever persisted (event would commit AFTER_COMMIT).
        await().pollInterval(ofSeconds(1)).atMost(ofSeconds(2)).untilAsserted(() ->
                assertThat(footprintAuditRepository.findByCorrelationId(correlationId)).isEmpty()
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void getFootprint_sharedComparisonGroupHeader_persistsThreeLinkedAuditRows() throws Exception {
        UUID shared = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/products/{productId}/footprint", "OFB-330")
                            .header("X-Comparison-Group", shared.toString())
                            .header("X-Correlation-Id", UUID.randomUUID().toString())
                            .param("asOf", "2026-07-15T00:00:00Z")
                            .param("materialWeightKg", "0.5")
                            .param("storageDays", "14")
                            .param("requiresRefrigeration", "true"))
                    .andExpect(status().isOk());
        }

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            long count = footprintAuditRepository.findAll().stream()
                    .filter(r -> shared.equals(r.getComparisonGroupId()))
                    .count();
            assertThat(count).isEqualTo(3);
        });
    }
}
