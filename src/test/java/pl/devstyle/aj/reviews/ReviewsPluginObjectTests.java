package pl.devstyle.aj.reviews;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.core.plugin.PluginDescriptor;
import pl.devstyle.aj.core.plugin.PluginDescriptorRepository;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ReviewsPluginObjectTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PluginDescriptorRepository pluginDescriptorRepository;

    private PluginDescriptor createAndSavePlugin() {
        var plugin = new PluginDescriptor();
        plugin.setId("reviews");
        plugin.setName("Product Reviews");
        plugin.setVersion("1.0.0");
        plugin.setUrl("http://localhost:3003");
        plugin.setEnabled(true);
        plugin.setManifest(Map.of("name", "Product Reviews", "version", "1.0.0"));
        return pluginDescriptorRepository.saveAndFlush(plugin);
    }

    @Test
    void saveReview_withProductBinding_returnsEntityFields() throws Exception {
        createAndSavePlugin();

        var body = Map.of(
                "rating", 5,
                "title", "Great",
                "body", "Excellent!",
                "reviewer", "Alice",
                "status", "PENDING"
        );

        mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}",
                        "reviews", "review", "rev-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectType").value("review"))
                .andExpect(jsonPath("$.entityType").value("PRODUCT"))
                .andExpect(jsonPath("$.entityId").value(42))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void listReviews_byProduct_returnsOnlyProductReviews() throws Exception {
        createAndSavePlugin();

        var body = Map.of("rating", 5, "status", "APPROVED");

        mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}",
                        "reviews", "review", "rev-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}",
                        "reviews", "review", "rev-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .param("entityType", "PRODUCT")
                        .param("entityId", "99"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/plugins/{pluginId}/objects/{objectType}",
                        "reviews", "review")
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].entityId").value(42));
    }

    @Test
    void listReviews_statusFilter_returnsOnlyApproved() throws Exception {
        createAndSavePlugin();

        mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}",
                        "reviews", "review", "rev-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 4, "status", "PENDING")))
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}",
                        "reviews", "review", "rev-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 5, "status", "APPROVED")))
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/plugins/{pluginId}/objects/{objectType}",
                        "reviews", "review")
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42")
                        .param("filter", "status:eq:APPROVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].data.status").value("APPROVED"));
    }

    @Test
    void listReviews_ratingFilter_returnsAboveThreshold() throws Exception {
        createAndSavePlugin();

        mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}",
                        "reviews", "review", "rev-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 2, "status", "APPROVED")))
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}",
                        "reviews", "review", "rev-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 5, "status", "APPROVED")))
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/plugins/{pluginId}/objects/{objectType}",
                        "reviews", "review")
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42")
                        .param("filter", "rating:gt:3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].data.rating").value(5));
    }

    @Test
    void listReviews_noFilter_returnsAllStatuses() throws Exception {
        createAndSavePlugin();

        mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}",
                        "reviews", "review", "rev-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 3, "status", "PENDING")))
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}",
                        "reviews", "review", "rev-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 5, "status", "APPROVED")))
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}",
                        "reviews", "review", "rev-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 1, "status", "REJECTED")))
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/plugins/{pluginId}/objects/{objectType}",
                        "reviews", "review")
                        .param("entityType", "PRODUCT")
                        .param("entityId", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }
}
