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
import pl.devstyle.aj.category.Category;
import pl.devstyle.aj.category.CategoryRepository;
import pl.devstyle.aj.core.plugin.PluginDescriptor;
import pl.devstyle.aj.core.plugin.PluginDescriptorRepository;
import pl.devstyle.aj.product.Product;
import pl.devstyle.aj.product.ProductRepository;

import java.math.BigDecimal;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ReviewsPluginDataTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PluginDescriptorRepository pluginDescriptorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

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

    private Category createAndSaveCategory(String name) {
        var category = new Category();
        category.setName(name);
        category.setDescription("Test category");
        return categoryRepository.saveAndFlush(category);
    }

    private Product createAndSaveProduct(String name, Category category) {
        var product = new Product();
        product.setName(name);
        product.setDescription("Test product");
        product.setPrice(new BigDecimal("9.99"));
        product.setSku(name.toLowerCase().replace(" ", "-") + "-sku");
        product.setCategory(category);
        return productRepository.saveAndFlush(product);
    }

    @Test
    void setRatingSummary_getData_returnsPersistedValues() throws Exception {
        createAndSavePlugin();
        var category = createAndSaveCategory("Electronics");
        var product = createAndSaveProduct("Test Product", category);

        var data = Map.of("rating", 4.2, "count", 15);

        mockMvc.perform(put("/api/plugins/{pluginId}/products/{productId}/data",
                        "reviews", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(data)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/plugins/{pluginId}/products/{productId}/data",
                        "reviews", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4.2))
                .andExpect(jsonPath("$.count").value(15));
    }

    @Test
    void setRatingSummary_overwritesPreviousData() throws Exception {
        createAndSavePlugin();
        var category = createAndSaveCategory("Electronics");
        var product = createAndSaveProduct("Test Product", category);

        mockMvc.perform(put("/api/plugins/{pluginId}/products/{productId}/data",
                        "reviews", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 3.0, "count", 5))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/plugins/{pluginId}/products/{productId}/data",
                        "reviews", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 4.5, "count", 10))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/plugins/{pluginId}/products/{productId}/data",
                        "reviews", product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4.5))
                .andExpect(jsonPath("$.count").value(10));
    }

    @Test
    void productListFilter_byRating_gte_returnsProductsAtOrAboveThreshold() throws Exception {
        createAndSavePlugin();
        var category = createAndSaveCategory("Electronics");
        var productA = createAndSaveProduct("Product Alpha", category);
        var productB = createAndSaveProduct("Product Beta", category);
        var productC = createAndSaveProduct("Product Gamma", category);

        mockMvc.perform(put("/api/plugins/{pluginId}/products/{productId}/data",
                        "reviews", productA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 4.5, "count", 10))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/plugins/{pluginId}/products/{productId}/data",
                        "reviews", productB.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 3.0, "count", 5))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/plugins/{pluginId}/products/{productId}/data",
                        "reviews", productC.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rating", 4.0, "count", 8))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products")
                        .param("pluginFilter", "reviews:rating:gte:4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + productA.getId() + ")]").exists())
                .andExpect(jsonPath("$[?(@.id == " + productB.getId() + ")]").doesNotExist())
                .andExpect(jsonPath("$[?(@.id == " + productC.getId() + ")]").exists());
    }
}
