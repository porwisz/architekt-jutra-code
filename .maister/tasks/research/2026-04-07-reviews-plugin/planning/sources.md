# Research Sources

## Category 1: plugin-architecture

Files that define the full data storage and lifecycle contract available to any plugin.

### Key Files (Backend Plugin Infrastructure)

| File | Purpose |
|------|---------|
| `src/main/java/pl/devstyle/aj/core/plugin/PluginObject.java` | JPA entity for plugin-owned custom objects; fields: pluginId, objectType, objectId, data (JSONB), entityType (enum), entityId (Long) |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectService.java` | Service layer for CRUD on plugin objects; list, listByEntity, get, save (upsert), delete |
| `src/main/java/pl/devstyle/aj/core/plugin/DbPluginObjectQueryService.java` | jOOQ query service with JSONB filter parsing (eq, gt, lt, exists, bool operators) |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectController.java` | REST endpoints at `/api/plugins/{pluginId}/objects` |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectRepository.java` | Spring Data repository for PluginObject |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginObjectResponse.java` | Response DTO shape |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptor.java` | JPA entity for plugin registration; fields: id (String PK), name, version, url, description, enabled, manifest (JSONB) |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptorService.java` | Plugin registration/lifecycle: uploadManifest, findAllEnabled, findEnabledOrThrow, setEnabled, delete |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginDescriptorRepository.java` | Spring Data repository for PluginDescriptor |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginController.java` | REST endpoints at `/api/plugins` for manifest registration and management |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginDataController.java` | REST endpoints at `/api/plugins/{pluginId}/products/{productId}/data` for per-product plugin data |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginDataService.java` | Service for per-product plugin data (reads/writes `products.pluginData` JSONB column) |
| `src/main/java/pl/devstyle/aj/core/plugin/EntityType.java` | Enum of host entity types plugins can bind objects to (PRODUCT, CATEGORY) |
| `src/main/java/pl/devstyle/aj/core/plugin/PluginResponse.java` | Response DTO for plugin registration |
| `src/main/java/pl/devstyle/aj/core/plugin/SetEnabledRequest.java` | Request record for toggling plugin enabled state |

### Grep Patterns for Discovery
- Pattern `PluginObject` in `src/main/java/` — find all usages and any extensions
- Pattern `EntityType` in `src/main/java/` — confirm enum values
- Pattern `pluginData` in `src/main/java/` — find all places per-product data is read/written

---

## Category 2: domain-patterns

Files that establish the Java coding conventions the reviews plugin backend must follow.

### Key Files (Entity and API Patterns)

| File | Purpose |
|------|---------|
| `src/main/java/pl/devstyle/aj/core/BaseEntity.java` | @MappedSuperclass with Long id (SEQUENCE), createdAt, @Version updatedAt — all domain entities extend this |
| `src/main/java/pl/devstyle/aj/core/JpaAuditingConfig.java` | Enables JPA auditing (@EnableJpaAuditing) — required for @CreatedDate/@Version to work |
| `src/main/java/pl/devstyle/aj/product/Product.java` | Concrete entity example: @SequenceGenerator, LAZY ManyToOne, JSONB pluginData column, business key equals/hashCode |
| `src/main/java/pl/devstyle/aj/category/Category.java` | Simpler entity example: String PK unique constraint, business key on name |
| `src/main/java/pl/devstyle/aj/product/ProductController.java` | REST controller pattern: @RestController, @RequestMapping, @Valid, @ResponseStatus |
| `src/main/java/pl/devstyle/aj/product/ProductService.java` | Service layer pattern: @Service, @Transactional, delegation to JPA repo and jOOQ query service |
| `src/main/java/pl/devstyle/aj/product/DbProductQueryService.java` | jOOQ read service pattern (Db*QueryService naming), JSONB filter parsing, bind parameters |
| `src/main/java/pl/devstyle/aj/product/ProductResponse.java` | Response record pattern |
| `src/main/java/pl/devstyle/aj/product/CreateProductRequest.java` | Request record with @NotBlank/@NotNull validation annotations |
| `src/main/java/pl/devstyle/aj/product/UpdateProductRequest.java` | Update request record pattern |
| `src/main/java/pl/devstyle/aj/product/ProductRepository.java` | Spring Data JPA repository pattern |
| `src/main/java/pl/devstyle/aj/core/error/GlobalExceptionHandler.java` | @RestControllerAdvice handling EntityNotFoundException, BusinessConflictException, IllegalArgumentException, validation errors |
| `src/main/java/pl/devstyle/aj/core/error/EntityNotFoundException.java` | Typed exception for 404 — pattern for custom plugin exception types |
| `src/main/java/pl/devstyle/aj/core/error/BusinessConflictException.java` | Typed exception for 409 — pattern for business rule violations |
| `src/main/java/pl/devstyle/aj/core/error/ErrorResponse.java` | Standard error response record shape |

### Standards Documents
| File | Purpose |
|------|---------|
| `.maister/docs/standards/backend/models.md` | JPA entity modeling: BaseEntity pattern, SEQUENCE PK, EnumType.STRING, LAZY fetch, Set collections, business key equals/hashCode |
| `.maister/docs/standards/backend/api.md` | REST API conventions: plural nouns, HTTP methods, status codes, URL nesting |
| `.maister/docs/standards/backend/jooq.md` | jOOQ standards: Db*QueryService pattern, bind variables, N+1 prevention, column projection |
| `.maister/docs/standards/global/validation.md` | Validation conventions: server-side required, early input checking, specific error messages |
| `.maister/docs/standards/global/error-handling.md` | Error handling: typed exceptions, centralized handling, clear messages |

---

## Category 3: frontend-plugin-sdk

Files that define the plugin frontend contract and provide concrete implementation examples.

### Key Files (SDK and Plugin Contracts)

| File | Purpose |
|------|---------|
| `plugins/sdk.ts` | Canonical SDK type declarations: PluginContext, PluginObject, PluginSDKType, thisPlugin.objects API, hostApp API |
| `plugins/CLAUDE.md` | Plugin development guide: project structure, manifest format, extension point shapes, SDK usage patterns, data storage patterns, CSS classes |
| `plugins/warehouse/manifest.json` | Working manifest example with 4 extension points (menu.main, product.detail.tabs, product.list.filters, product.detail.info) |
| `plugins/box-size/manifest.json` | Minimal manifest with 2 extension points (product.detail.tabs, product.detail.info) |
| `plugins/warehouse/src/domain.ts` | Domain type pattern: interfaces (Warehouse, StockEntry, Product) and mapper functions (toWarehouse, toStockEntry) from PluginObject |
| `plugins/box-size/src/domain.ts` | Simpler domain type pattern: BoxDimensions interface, toBoxDimensions mapper, formatBox display helper |
| `plugins/warehouse/src/main.tsx` | Router pattern: BrowserRouter + Routes matching manifest paths |
| `plugins/warehouse/src/pages/WarehousePage.tsx` | Full CRUD pattern: objects.list, objects.save (with entity binding), objects.delete, setData for pluginData |
| `plugins/warehouse/src/pages/ProductStockTab.tsx` | product.detail.tabs pattern: reading productId from context, fetching entity-bound objects |
| `plugins/warehouse/src/pages/ProductAvailability.tsx` | product.detail.info pattern: compact display component using pluginData |
| `plugins/box-size/src/pages/ProductBoxTab.tsx` | getData/setData pattern (simpler than objects API) — tab form for per-product data |
| `plugins/box-size/src/pages/ProductBoxBadge.tsx` | Compact badge for product.detail.info extension point |
| `plugins/warehouse/package.json` | Plugin package.json template |
| `plugins/warehouse/vite.config.ts` | Vite configuration for plugin builds |

### Extension Point Reference (from CLAUDE.md)

| Extension Point | Description | Context Available |
|----------------|-------------|-------------------|
| `menu.main` | Full-page sidebar navigation entry | pluginId, pluginName, hostOrigin |
| `product.detail.tabs` | Tab on product detail view | productId, pluginId, hostOrigin |
| `product.detail.info` | Inline info below product card (~60px) | productId, pluginId, hostOrigin |
| `product.list.filters` | Native filter control on product list (host-rendered, no iframe) | filterKey, filterType in manifest |

### Candidate Extension Points for Reviews Plugin
- `product.detail.tabs` — main review list + submission form (path: `/product-reviews`)
- `product.detail.info` — compact star rating badge (path: `/product-rating-badge`)
- `product.list.filters` — filter products by minimum rating (filterKey: derived field in pluginData, filterType: `number`)
- `menu.main` — optional admin view for review moderation (path: `/`, icon: `star`)

---

## Category 4: migration-and-testing

Files that establish migration numbering conventions and integration test patterns.

### Key Files (Liquibase Migrations)

| File | Purpose |
|------|---------|
| `src/main/resources/db/changelog/db.changelog-master.yaml` | Master changelog file — shows how individual changelogs are included |
| `src/main/resources/db/changelog/2026/001-create-categories-table.yaml` | First migration: sequence + table creation pattern with rollback |
| `src/main/resources/db/changelog/2026/002-create-products-table.yaml` | FK reference pattern and more complex table |
| `src/main/resources/db/changelog/2026/004-create-plugins-table.yaml` | JSONB column pattern, boolean defaults |
| `src/main/resources/db/changelog/2026/005-add-plugin-data-to-products.yaml` | Additive column migration pattern (adding JSONB to existing table) |
| `src/main/resources/db/changelog/2026/006-create-plugin-objects-table.yaml` | Sequence + table + FK + unique constraint + index pattern |
| `src/main/resources/db/changelog/2026/007-add-entity-binding-to-plugin-objects.yaml` | Adding columns + indexes pattern; demonstrates addColumn with rollback |

**Current highest migration number:** `007` — new reviews-related migration would be `008` (if needed).

**Migration conventions observed:**
- Filename: `{NNN}-{verb}-{noun}.yaml` (e.g., `008-create-reviews-objects-index.yaml`)
- changeSet id: mirrors filename without extension
- author: `aj`
- Every changeSet has a rollback block
- Sequences created in a separate changeSet from the table
- Indexes created within the table changeSet

### Key Files (Integration Tests)

| File | Purpose |
|------|---------|
| `src/test/java/pl/devstyle/aj/TestcontainersConfiguration.java` | PostgreSQL container setup — import into all integration test classes |
| `src/test/java/pl/devstyle/aj/IntegrationTests.java` | Base integration test pattern: @Import(TestcontainersConfiguration), @SpringBootTest(MOCK), @AutoConfigureMockMvc |
| `src/test/java/pl/devstyle/aj/core/plugin/PluginDataAndObjectsIntegrationTests.java` | Plugin object CRUD integration test with createAndSavePlugin() helper pattern |
| `src/test/java/pl/devstyle/aj/core/plugin/PluginObjectEntityBindingTests.java` | Entity binding tests — closest to what reviews tests will need |
| `src/test/java/pl/devstyle/aj/core/plugin/PluginObjectApiAndFilterTests.java` | JSONB filter tests — validates the filter operators used by reviews rating filter |
| `src/test/java/pl/devstyle/aj/category/CategoryIntegrationTests.java` | Domain entity CRUD test pattern with @Transactional, createAndSave* helpers |
| `src/test/java/pl/devstyle/aj/category/CategoryValidationTests.java` | Validation-specific test class pattern — separate class for validation edge cases |
| `src/test/java/pl/devstyle/aj/product/ProductIntegrationTests.java` | More complex entity tests with category dependency — helper method chaining |

**Test conventions observed:**
- Test class suffix: `*Tests` (not `*Test`)
- Package mirrors production package
- All integration tests: `@Import(TestcontainersConfiguration.class)`, `@SpringBootTest(webEnvironment = MOCK)`, `@AutoConfigureMockMvc`, `@Transactional`
- Helper method naming: `createAndSave{Entity}(...)` — private, returns saved entity
- Test method naming: `{action}_{condition}_{expectedResult}`
- Validation tests split into separate class: `{Entity}ValidationTests`
- MockMvc + jsonPath + Hamcrest for HTTP assertions

---

## Standards Documents (Cross-Cutting)

| File | Relevant To |
|------|------------|
| `.maister/docs/standards/backend/models.md` | Entity design for any potential dedicated reviews entity |
| `.maister/docs/standards/backend/jooq.md` | DbReviewQueryService pattern if aggregation queries are needed |
| `.maister/docs/standards/backend/migrations.md` | Migration rules: reversible, focused, descriptive names, version controlled |
| `.maister/docs/standards/testing/backend-testing.md` | Integration test strategy: test naming, helper methods, what NOT to test |
| `.maister/docs/standards/global/conventions.md` | General conventions: predictable structure, environment variables, minimal dependencies |
| `.maister/docs/standards/global/minimal-implementation.md` | Build only what is needed — relevant for scoping the reviews plugin surface |
