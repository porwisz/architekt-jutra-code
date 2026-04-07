# Migration and Testing Findings

## 1. Liquibase Migration Conventions

### 1.1 Master Changelog

**Source**: `src/main/resources/db/changelog/db.changelog-master.yaml:1-4`

```yaml
databaseChangeLog:
  - includeAll:
      path: ./2026
      relativeToChangelogFile: true
```

The master file uses `includeAll` on a year-based subdirectory (`./2026`). All individual migration files live under `src/main/resources/db/changelog/2026/`. Liquibase applies them in filename sort order, so the numeric prefix determines execution order.

### 1.2 Existing Migration Files and Numbering

**Source**: `src/main/resources/db/changelog/2026/` directory listing

| File | changeSet IDs |
|------|--------------|
| `001-create-categories-table.yaml` | `001-create-category-seq`, `001-create-categories-table` |
| `002-create-products-table.yaml` | (not read, inferred from pattern) |
| `003-insert-sample-data.yaml` | (not read, inferred from pattern) |
| `004-create-plugins-table.yaml` | `004-create-plugins-table` |
| `005-add-plugin-data-to-products.yaml` | (not read, inferred from pattern) |
| `006-create-plugin-objects-table.yaml` | `006-create-plugin-object-seq`, `006-create-plugin-objects-table` |
| `007-add-entity-binding-to-plugin-objects.yaml` | `007-add-entity-binding-columns` |

**Next migration number: `008`**

The filename for the reviews plugin's initial migration must be:
```
008-create-reviews-table.yaml
```

### 1.3 Numbering and Naming Format

- **File name pattern**: `{NNN}-{kebab-case-description}.yaml` (zero-padded 3 digits)
- **changeSet id pattern**: `{NNN}-{kebab-case-description}` — matches the logical operation within the file, not necessarily the filename
- **author**: always `aj`
- **Multiple changeSets per file**: allowed and used — `006` has two changeSets (sequence creation + table creation), each with its own `id`

### 1.4 YAML Structure: Table with JSONB Column

**Source**: `src/main/resources/db/changelog/2026/006-create-plugin-objects-table.yaml:1-83` and `004-create-plugins-table.yaml:1-52`

Both `plugins.manifest` (006 pattern via 004) and `plugin_objects.data` (006) are JSONB columns. The JSONB column declaration is:

```yaml
- column:
    name: data
    type: JSONB
    constraints:
      nullable: false
```

No special `dbms` type override is needed — `JSONB` is used directly as the type string.

### 1.5 Standard Table Structure Pattern

Every table in the project follows a consistent column set:

**Source**: `src/main/resources/db/changelog/2026/001-create-categories-table.yaml:14-51` and `006-create-plugin-objects-table.yaml:14-83`

1. `id` — `BIGINT`, `primaryKey: true`, `nullable: false`, populated from a dedicated sequence (`{table_singular}_seq`)
2. Business columns (VARCHAR, TEXT, BIGINT, JSONB, etc.)
3. `created_at` — `TIMESTAMP`, `nullable: false`
4. `updated_at` — `TIMESTAMP`, `nullable: false`

Sequence changeSets always come before the table changeSet within the same file.

### 1.6 Rollback is Always Explicit

Every changeSet includes a `rollback:` block.

- For table creation: `dropTable`
- For sequence creation: `dropSequence`
- For `addColumn`: `dropColumn` for each added column
- For index creation (as part of an `addColumn` migration): `dropIndex` for each index

**Source**: `007-add-entity-binding-to-plugin-objects.yaml:37-49` shows rollback for addColumn + multiple indexes.

### 1.7 Index and Constraint Naming Conventions

**Source**: `006-create-plugin-objects-table.yaml:57-83`

- Foreign key: `fk_{table}_{column}`  → `fk_plugin_objects_plugin_id`
- Unique constraint: `uk_{table}_{columns_abbrev}` → `uk_plugin_objects_plugin_type_id`
- Index: `idx_{table}_{purpose}` → `idx_plugin_objects_plugin_id`, `idx_plugin_objects_plugin_type`

### 1.8 Exact Migration YAML Template for Reviews Plugin

Based on patterns from `006-create-plugin-objects-table.yaml` and `001-create-categories-table.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: 008-create-review-seq
      author: aj
      changes:
        - createSequence:
            sequenceName: review_seq
            startValue: 1
            incrementBy: 1
      rollback:
        - dropSequence:
            sequenceName: review_seq

  - changeSet:
      id: 008-create-reviews-table
      author: aj
      changes:
        - createTable:
            tableName: reviews
            columns:
              - column:
                  name: id
                  type: BIGINT
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: plugin_id
                  type: VARCHAR(255)
                  constraints:
                    nullable: false
              - column:
                  name: entity_type
                  type: VARCHAR(50)
                  constraints:
                    nullable: false
              - column:
                  name: entity_id
                  type: BIGINT
                  constraints:
                    nullable: false
              - column:
                  name: author_id
                  type: BIGINT
              - column:
                  name: rating
                  type: INTEGER
                  constraints:
                    nullable: false
              - column:
                  name: content
                  type: TEXT
              - column:
                  name: data
                  type: JSONB
                  constraints:
                    nullable: false
              - column:
                  name: status
                  type: VARCHAR(50)
                  constraints:
                    nullable: false
              - column:
                  name: created_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false
              - column:
                  name: updated_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false
        - addForeignKeyConstraint:
            baseTableName: reviews
            baseColumnNames: plugin_id
            referencedTableName: plugins
            referencedColumnNames: id
            constraintName: fk_reviews_plugin_id
        - createIndex:
            tableName: reviews
            indexName: idx_reviews_entity
            columns:
              - column:
                  name: entity_type
              - column:
                  name: entity_id
        - createIndex:
            tableName: reviews
            indexName: idx_reviews_plugin_entity
            columns:
              - column:
                  name: plugin_id
              - column:
                  name: entity_type
              - column:
                  name: entity_id
      rollback:
        - dropTable:
            tableName: reviews
```

Adjust columns to match the actual reviews domain model. The template above is illustrative; the critical structural rules are: sequence first, table second, rollback on every changeSet, JSONB with no special dbms qualifier, `created_at`/`updated_at` always last.

---

## 2. Integration Test Infrastructure

### 2.1 TestcontainersConfiguration

**Source**: `src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java:1-18`

```java
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    }
}
```

A shared `@TestConfiguration` class spins up a real PostgreSQL 18 container via Testcontainers. Every integration test imports this with `@Import(TestcontainersConfiguration.class)`.

### 2.2 Standard Class-Level Annotations

**Source**: `src/test/java/pl/devstyle/aj/category/CategoryIntegrationTests.java:24-28`, `src/test/java/pl/devstyle/aj/core/plugin/PluginDataAndObjectsIntegrationTests.java:29-33`, `src/test/java/pl/devstyle/aj/core/plugin/PluginObjectApiAndFilterTests.java:23-27`

All integration test classes that interact with MockMvc use exactly these four annotations:

```java
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ReviewsIntegrationTests {
```

- `@Import(TestcontainersConfiguration.class)` — wires the Postgres container
- `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)` — full application context with mock servlet
- `@AutoConfigureMockMvc` — injects `MockMvc`
- `@Transactional` — rolls back each test, providing isolation without manual cleanup

Tests that only use services directly (no MockMvc) omit `@AutoConfigureMockMvc` but keep the other three. **Source**: `src/test/java/pl/devstyle/aj/core/plugin/PluginObjectEntityBindingTests.java:14-17`.

### 2.3 Standard Field Injections

**Source**: `CategoryIntegrationTests.java:30-37`, `PluginDataAndObjectsIntegrationTests.java:35-47`, `PluginObjectApiAndFilterTests.java:30-37`

```java
@Autowired
private MockMvc mockMvc;

@Autowired
private ObjectMapper objectMapper;    // tools.jackson.databind.ObjectMapper (not com.fasterxml)

@Autowired
private ReviewRepository reviewRepository;  // for createAndSave* helpers
```

Note: the import for ObjectMapper is `tools.jackson.databind.ObjectMapper` (Jackson 3 / Spring Boot 4), not the traditional `com.fasterxml.jackson.databind.ObjectMapper`.

### 2.4 createAndSave* Helper Method Pattern

Every test class defines private helpers that build and persist entities using `saveAndFlush`. The pattern is consistent across all test files.

**Source**: `CategoryIntegrationTests.java:39-44`

```java
private Category createAndSaveCategory(String name, String description) {
    var category = new Category();
    category.setName(name);
    category.setDescription(description);
    return categoryRepository.saveAndFlush(category);
}
```

**Source**: `PluginDataAndObjectsIntegrationTests.java:50-75` (multi-step helper that saves a prerequisite first):

```java
private PluginDescriptor createAndSavePlugin(String id) {
    var plugin = new PluginDescriptor();
    plugin.setId(id);
    plugin.setName(id + " plugin");
    plugin.setVersion("1.0.0");
    plugin.setUrl("http://localhost:3001");
    plugin.setDescription("Test plugin");
    plugin.setEnabled(true);
    plugin.setManifest(Map.of("name", id + " plugin", "version", "1.0.0"));
    return pluginDescriptorRepository.saveAndFlush(plugin);
}
```

Rules:
- Method is `private`, returns the entity
- Uses `var` for local variables
- Calls `saveAndFlush` (not `save`) to guarantee visibility to the current transaction
- For entities requiring a parent (e.g., Product requires Category), the helper creates and saves the parent inline

### 2.5 Test Method Naming Convention

**Source**: `CategoryIntegrationTests.java:47-58`, `PluginObjectApiAndFilterTests.java:67-81`, `PluginObjectEntityBindingTests.java:41-49`

Pattern: `action_condition_expectedResult`

Examples from the codebase:
- `createCategory_returns201WithCategoryResponse`
- `getCategoryById_returns200`
- `getNonExistentCategory_returns404WithErrorResponse`
- `deleteCategory_returns204`
- `deleteCategoryWithProducts_returns409`
- `createDuplicateName_returns409`
- `save_withEntityBinding_returnsEntityTypeAndEntityId`
- `save_withoutEntityParams_returnsNullEntityFields`
- `list_withEntityFilter_returnsOnlyMatchingObjects`
- `list_withJsonbFilter_returnsOnlyMatchingObjects`
- `save_responseIncludesAllExpectedFields`

### 2.6 MockMvc Assertion Patterns

**Source**: `CategoryIntegrationTests.java:46-135`, `PluginObjectApiAndFilterTests.java:67-157`, `PluginObjectGapTests.java:69-187`

**Status assertions**:
```java
.andExpect(status().isOk())          // 200
.andExpect(status().isCreated())     // 201
.andExpect(status().isNoContent())   // 204
.andExpect(status().isNotFound())    // 404
.andExpect(status().isBadRequest())  // 400
.andExpect(status().isConflict())    // 409
```

**Field value assertions**:
```java
.andExpect(jsonPath("$.id").value(notNullValue()))
.andExpect(jsonPath("$.name").value("Electronics"))
.andExpect(jsonPath("$.status").value(404))
.andExpect(jsonPath("$.createdAt").value(notNullValue()))
```

**Array assertions**:
```java
.andExpect(jsonPath("$").isArray())
.andExpect(jsonPath("$", hasSize(1)))
.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
.andExpect(jsonPath("$[0].objectId").value("rev-1"))
```

**Absence assertions**:
```java
.andExpect(jsonPath("$.title").doesNotExist())
.andExpect(jsonPath("$.pluginData.reviews").doesNotExist())
```

**Error response shape** (from `CategoryIntegrationTests.java:85-90`):
```java
.andExpect(jsonPath("$.status").value(404))
.andExpect(jsonPath("$.error").value("Not Found"))
.andExpect(jsonPath("$.message").value(notNullValue()))
```

**Hamcrest matchers used**:
- `notNullValue()` — from `org.hamcrest.Matchers`
- `hasSize(int)` — from `org.hamcrest.Matchers`
- `greaterThanOrEqualTo(int)` — from `org.hamcrest.Matchers`

**Request construction**:
```java
mockMvc.perform(post("/api/reviews")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(request)))
```

For path variables with query parameters:
```java
mockMvc.perform(put("/api/plugins/{pluginId}/objects/{objectType}/{objectId}?entityType={entityType}&entityId={entityId}",
        pluginId, objectType, objectId, entityType, entityId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(data)))
```

### 2.7 Test Data Isolation

**Source**: `CategoryIntegrationTests.java:27`, `PluginDataAndObjectsIntegrationTests.java:32`, all other test classes

`@Transactional` at the class level rolls back the database after every test method. No `@BeforeEach` / `@AfterEach` cleanup is used. The only exception to this is when `saveAndFlush` is needed to force SQL execution within the same transaction so that the test-subject code can see the data.

One special case exists: when a test needs to verify a cascade-delete constraint, an explicit `categoryRepository.flush()` is called before inserting via native query, to ensure the FK is visible. **Source**: `CategoryIntegrationTests.java:117-118`.

### 2.8 Integration Test for Reviews Plugin — Complete Template

```java
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
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Transactional
class ReviewsIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private PluginDescriptorRepository pluginDescriptorRepository;

    private PluginDescriptor createAndSavePlugin(String id) {
        var plugin = new PluginDescriptor();
        plugin.setId(id);
        plugin.setName(id + " plugin");
        plugin.setVersion("1.0.0");
        plugin.setUrl("http://localhost:3001");
        plugin.setDescription("Test plugin");
        plugin.setEnabled(true);
        plugin.setManifest(Map.of("name", id + " plugin", "version", "1.0.0"));
        return pluginDescriptorRepository.saveAndFlush(plugin);
    }

    private Review createAndSaveReview(String pluginId, Long entityId, int rating, String content) {
        var review = new Review();
        // set fields...
        return reviewRepository.saveAndFlush(review);
    }

    @Test
    void createReview_returns201WithReviewResponse() throws Exception {
        var plugin = createAndSavePlugin("reviews");

        var request = Map.of("entityId", 1L, "rating", 5, "content", "Excellent!");

        mockMvc.perform(post("/api/plugins/{pluginId}/reviews", plugin.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.content").value("Excellent!"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()));
    }

    @Test
    void getNonExistentReview_returns404WithErrorResponse() throws Exception {
        var plugin = createAndSavePlugin("reviews");

        mockMvc.perform(get("/api/plugins/{pluginId}/reviews/{id}", plugin.getId(), 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value(notNullValue()));
    }
}
```

---

## 3. Summary of Key Facts

| Question | Answer |
|----------|--------|
| Next migration number | `008` |
| Migration filename | `008-create-reviews-table.yaml` |
| Location | `src/main/resources/db/changelog/2026/` |
| Master changelog includes | via `includeAll` on `./2026` subdirectory |
| changeSet author | `aj` |
| JSONB column type string | `JSONB` (no dbms qualifier needed) |
| Sequence incrementBy | `1` |
| Rollback required | yes, always explicit |
| Test class annotations (with MockMvc) | `@Import`, `@SpringBootTest(MOCK)`, `@AutoConfigureMockMvc`, `@Transactional` |
| Test class annotations (service-only) | `@Import`, `@SpringBootTest(MOCK)`, `@Transactional` |
| ObjectMapper import | `tools.jackson.databind.ObjectMapper` |
| Postgres container version | `postgres:18` |
| Test data isolation | `@Transactional` rollback; `saveAndFlush` within helpers |
| Test naming pattern | `action_condition_expectedResult` |
| Package placement | same package as production class (package-private test class) |
