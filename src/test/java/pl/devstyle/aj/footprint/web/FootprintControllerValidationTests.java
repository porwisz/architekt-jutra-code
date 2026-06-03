package pl.devstyle.aj.footprint.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.SecurityMockMvcConfiguration;
import pl.devstyle.aj.TestcontainersConfiguration;
import pl.devstyle.aj.WithMockEditUser;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import({TestcontainersConfiguration.class, SecurityMockMvcConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
@WithMockEditUser
class FootprintControllerValidationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getFootprint_strictModeMissingFactor_returns422ProblemJson() throws Exception {
        mockMvc.perform(get("/api/products/{productId}/footprint", "OFB-330")
                        .param("asOf", "2028-01-15T00:00:00Z")
                        .param("materialWeightKg", "0.5"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_FACTOR"))
                .andExpect(jsonPath("$.componentId").value(notNullString()))
                .andExpect(jsonPath("$.type").value(containsString("missing-factor")))
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").exists())
                .andExpect(jsonPath("$.instance").exists());
    }

    @Test
    void getFootprint_lenientModeMissingLastMileFactor_returns200WithLeafWarning() throws Exception {
        mockMvc.perform(get("/api/products/{productId}/footprint", "OFB-330")
                        .param("asOf", "2028-01-15T00:00:00Z")
                        .param("materialWeightKg", "0.5")
                        .param("strictness", "LENIENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown.componentId").value("product-footprint"))
                // Find any descendant warning with code=MISSING_FACTOR
                .andExpect(jsonPath("$..warnings[?(@.code=='MISSING_FACTOR')]").exists());
    }

    @Test
    void getFootprint_negativeMaterialWeight_returns400InvalidParameters() throws Exception {
        mockMvc.perform(get("/api/products/{productId}/footprint", "OFB-330")
                        .param("materialWeightKg", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETERS"))
                .andExpect(jsonPath("$.parameter").value("materialWeightKg"));
    }

    @Test
    void getFootprint_unknownProductId_returns422MissingAttribute() throws Exception {
        mockMvc.perform(get("/api/products/{productId}/footprint", "UNKNOWN-1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("MISSING_PRODUCT_ATTRIBUTE"))
                .andExpect(jsonPath("$.productId").value("UNKNOWN-1"));
    }

    private static org.hamcrest.Matcher<String> notNullString() {
        return new org.hamcrest.BaseMatcher<>() {
            @Override
            public boolean matches(Object o) {
                return o != null;
            }

            @Override
            public void describeTo(org.hamcrest.Description description) {
                description.appendText("non-null value");
            }
        };
    }
}
