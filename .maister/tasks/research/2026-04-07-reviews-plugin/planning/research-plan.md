# Research Plan: Reviews Plugin for Microkernel Platform

## Research Overview

### Research Question
How should a new "reviews" plugin be designed and implemented for the existing microkernel-based Spring Boot platform?

### Research Type
Mixed — technical investigation (existing plugin contracts and data storage infrastructure) combined with requirements/domain research (reviews domain model design) and literature research (best practices for review systems built on plugin object storage patterns).

### Scope and Boundaries

**Included:**
- Plugin registration and manifest contract (`PUT /api/plugins/{pluginId}/manifest`)
- Plugin SDK data storage patterns (`thisPlugin.getData/setData`, `thisPlugin.objects.*`)
- Extension points applicable to a reviews plugin (`product.detail.tabs`, `product.detail.info`, `product.list.filters`)
- Backend plugin storage infrastructure: `plugin_objects` table, `PluginObject` entity, `PluginObjectService`, `DbPluginObjectQueryService`
- JPA entity modeling patterns from existing domain entities (`BaseEntity`, `Category`, `Product`)
- Liquibase migration conventions from existing changelogs
- REST API conventions from `ProductController`, `CategoryController`, `PluginObjectController`
- Error handling and validation patterns from `GlobalExceptionHandler`
- Test infrastructure and patterns from existing integration tests
- Reviews domain model: rating scale, reviewer identity, review subject (product), review lifecycle
- JSONB filter queries applicable to reviews (e.g., rating range filter on product list)

**Excluded:**
- Third-party review platforms
- Frontend implementation details
- Non-plugin approaches

**Constraints:**
- Java 25, Spring Boot 4.0.5, PostgreSQL
- Must use the existing plugin manifest + extension point contract
- Must use the existing plugin data storage infrastructure (no new first-class tables unless justified)
- Must follow standards in `.maister/docs/standards/`

---

## Methodology

### Primary Approach
**Codebase analysis + domain modeling**

The platform is in an advanced state for a pre-alpha: the plugin object infrastructure is fully implemented and battle-tested against two working plugins. The reviews plugin research must therefore:
1. Map the exact contracts the platform provides (manifest, SDK API, object storage, entity binding)
2. Determine whether the `plugin_objects` + JSONB pattern is sufficient for reviews or whether a dedicated table is warranted
3. Design the reviews domain model to fit within that infrastructure
4. Derive the manifest, extension points, and REST API design from patterns in working plugins

### Fallback Strategies
- If `plugin_objects` JSONB storage is insufficient for the reviews domain (e.g., rating aggregation queries), investigate adding a dedicated `reviews` table as a plugin-owned schema migration under a plugin-specific Liquibase module.
- If inter-plugin communication is needed (e.g., reviews referencing warehouse stock), investigate the `hostApp.fetch` SDK mechanism and the `EntityType` enum extension point.

### Analysis Framework
1. **Contract extraction** — What does the platform guarantee to any plugin? (manifest shape, SDK API, object storage semantics)
2. **Storage fit assessment** — Can the reviews domain (review entity, rating, reviewer, subject) be modeled cleanly with `plugin_objects` + JSONB? What are the trade-offs vs a dedicated table?
3. **Domain model design** — Enumerate entities, value objects, enumerations, and relationships for the reviews domain
4. **API surface design** — Define REST endpoints following project conventions (`/api/plugins/{pluginId}/...`)
5. **Migration strategy** — If a dedicated table is needed, document the Liquibase changeset pattern
6. **Cross-plugin dependencies** — Identify any references to `Product`, `Category`, or other plugin data

---

## Research Phases

### Phase 1: Broad Discovery
**Goal:** Map all files relevant to the plugin contract and storage infrastructure.

- Glob all files under `src/main/java/pl/devstyle/aj/core/plugin/`
- Glob all Liquibase changelogs under `src/main/resources/db/changelog/`
- Glob all plugin frontend files: `plugins/warehouse/src/`, `plugins/box-size/src/`
- Glob all integration tests under `src/test/java/pl/devstyle/aj/core/plugin/`
- Read `plugins/CLAUDE.md` and `plugins/sdk.ts` for the canonical plugin contract

### Phase 2: Targeted Reading
**Goal:** Understand implementation details of plugin storage and domain entity patterns.

- Read `PluginObject.java`, `PluginObjectService.java`, `DbPluginObjectQueryService.java` — storage semantics and JSONB filter capabilities
- Read `PluginDescriptor.java`, `PluginDescriptorService.java` — manifest registration lifecycle
- Read `PluginObjectController.java`, `PluginDataController.java` — REST API surface available to plugins
- Read `BaseEntity.java` — entity base class pattern (id, createdAt, @Version updatedAt, SEQUENCE generator)
- Read `Product.java`, `Category.java` — concrete entity patterns and pluginData JSONB column
- Read `DbProductQueryService.java` — JSONB filter query pattern used for product list filtering
- Read `GlobalExceptionHandler.java` — error handling conventions
- Read `CategoryController.java` or `ProductController.java` — REST API naming and structure patterns
- Read migration changelogs `006-create-plugin-objects-table.yaml`, `007-add-entity-binding-to-plugin-objects.yaml` — Liquibase conventions
- Read `plugins/warehouse/src/domain.ts`, `plugins/box-size/src/domain.ts` — frontend domain type patterns
- Read `plugins/warehouse/manifest.json`, `plugins/box-size/manifest.json` — manifest extension point shapes

### Phase 3: Deep Dive
**Goal:** Evaluate storage options and design the reviews domain model.

**Storage option analysis:**
- Evaluate `plugin_objects` + JSONB for reviews: Can it handle rating aggregation? Can it support queries like "products with average rating >= 4"? What are the index implications?
- Evaluate `thisPlugin.getData/setData` (per-product JSONB in `products.pluginData`) for lightweight rating summaries (e.g., storing `{ averageRating: 4.2, reviewCount: 15 }`)
- Consider hybrid: individual reviews as `plugin_objects` (type: "review") + per-product rating summary in `pluginData`

**Domain model design:**
- Review entity fields: `reviewId` (objectId), `productId`, `rating` (1–5 integer), `title`, `body`, `reviewerName`, optional `reviewerEmail`, `status` (PENDING/APPROVED/REJECTED)
- Entity binding: reviews bound to `PRODUCT` entity type, enabling `objects.list("review", { entityType: "PRODUCT", entityId: productId })`
- JSONB filter expressions for product list filter: `rating:gt:3`, `status:eq:APPROVED`
- Manifest extension points: `product.detail.tabs` (review list + form), `product.detail.info` (star rating badge), `product.list.filters` (minimum rating filter), optional `menu.main` (reviews admin page)

**API design:**
- Plugin relies on `thisPlugin.objects` SDK for all CRUD (no custom backend endpoints needed unless aggregation is required)
- If aggregation is needed: custom `hostApp.fetch("/api/plugins/{pluginId}/reviews/summary")` — requires a new backend endpoint

### Phase 4: Verification
**Goal:** Cross-reference design decisions against existing patterns and standards.

- Verify entity binding pattern against `PluginObjectEntityBindingTests.java`
- Verify JSONB filter pattern against `PluginObjectApiAndFilterTests.java` and `DbPluginObjectQueryService.parseFilter`
- Verify manifest shape against `PluginDescriptorService.uploadManifest` validation
- Verify JPA patterns against `.maister/docs/standards/backend/models.md`
- Verify API design against `.maister/docs/standards/backend/api.md`
- Verify migration format against existing changelogs and `.maister/docs/standards/backend/migrations.md`

---

## Gathering Strategy

### Instances: 4

| # | Category ID | Focus Area | Tools | Output Prefix |
|---|------------|------------|-------|---------------|
| 1 | plugin-architecture | Backend plugin infrastructure: PluginObject entity, PluginObjectService, DbPluginObjectQueryService, PluginDataService, PluginController, PluginObjectController, PluginDescriptorService, EntityType enum, PluginObjectRepository. Goal: understand every data operation available to a plugin via the host. | Glob, Read, Grep | plugin-architecture |
| 2 | domain-patterns | Existing domain entity patterns and conventions: BaseEntity, Product, Category, ProductController, CategoryController, ProductService, DbProductQueryService, GlobalExceptionHandler, CreateProductRequest, ProductResponse. Goal: extract the exact JPA, REST, validation, and error-handling patterns the reviews plugin must follow. | Read, Grep | domain-patterns |
| 3 | frontend-plugin-sdk | Plugin frontend contracts: plugins/sdk.ts, plugins/CLAUDE.md, warehouse manifest + domain + pages, box-size manifest + domain + pages. Goal: understand what extension points make sense for reviews, how the SDK object API is used in practice, and what the reviews plugin frontend structure should look like. | Read | frontend-plugin-sdk |
| 4 | migration-and-testing | Liquibase migration conventions (all changelogs in db/changelog/2026/), integration test patterns (PluginDataAndObjectsIntegrationTests, PluginObjectEntityBindingTests, PluginObjectApiAndFilterTests, CategoryIntegrationTests). Goal: establish migration numbering/format and test class structure for the reviews plugin. | Read, Grep | migration-and-testing |

### Rationale
Four gatherers map cleanly to the four distinct knowledge areas needed before implementation: the host-side storage contract (category 1), the Java coding patterns to replicate (category 2), the frontend SDK contract (category 3), and the infrastructure conventions for migrations and tests (category 4). There is no overlap — each gatherer operates on a disjoint file set.

---

## Success Criteria

1. **Plugin contract understood:** The manifest shape, all SDK data operations, and the entity binding mechanism are fully documented with concrete field names and constraints.
2. **Storage decision reached:** A clear recommendation on whether to use `plugin_objects` only, `pluginData` + `plugin_objects` hybrid, or a dedicated table — with rationale referencing specific platform capabilities and limitations.
3. **Domain model defined:** All fields, types, enumerations (e.g., `ReviewStatus`), and JSONB filter keys for the reviews domain are specified.
4. **Extension points specified:** Which of `product.detail.tabs`, `product.detail.info`, `product.list.filters`, `menu.main` the reviews plugin will use, with manifest JSON shape for each.
5. **API surface defined:** Every SDK call the plugin frontend will make is listed; any custom backend endpoints (if aggregation requires them) are identified with path, method, and response shape.
6. **Migration plan ready:** If a dedicated table is needed, the Liquibase changeset structure is specified following existing conventions; if not, this is explicitly documented as not needed.
7. **Test strategy outlined:** Integration test class names, helper method patterns, and which behaviors need coverage are identified.

---

## Expected Outputs

- **Research report** (`analysis/findings/`) with findings per gathering category
- **Recommendations** for reviews plugin design: manifest, domain model, storage strategy, API surface, migration plan, test strategy
- **Implementation-ready specification** sufficient to feed into the development workflow without further research
