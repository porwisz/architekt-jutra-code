# Plugin Architecture Findings

## Research Scope

All source files are under `src/main/java/pl/devstyle/aj/core/plugin/` unless noted otherwise.

---

## 1. PluginDescriptor Entity (`PluginDescriptor.java`)

**Table**: `plugins`
**Primary key**: `VARCHAR(255) id` — application-assigned, NOT a sequence. The plugin registers itself using a chosen string ID.

### Columns

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | VARCHAR(255) | NO | Business key; equals/hashCode based on `id` |
| `name` | VARCHAR(255) | NO | Human-readable name |
| `version` | VARCHAR(50) | YES | Semantic version string |
| `url` | VARCHAR(500) | YES | Plugin's frontend URL (must be HTTP/HTTPS) |
| `description` | TEXT | YES | Free text description |
| `enabled` | BOOLEAN | NO | Defaults to `true` on creation |
| `manifest` | JSONB | NO | Full manifest JSON blob |
| `created_at` | TIMESTAMP | NO | Populated by `@CreatedDate` (JPA Auditing) |
| `updated_at` | TIMESTAMP | NO | `@Version` column — optimistic locking + audit |

**Source**: `PluginDescriptor.java:1-72`, migration `004-create-plugins-table.yaml`

### Manifest Shape

The manifest is a free-form JSONB map. The following keys are explicitly extracted and mapped to entity columns:

| Manifest key | Mapped to | Validation |
|---|---|---|
| `name` | `PluginDescriptor.name` | Required, non-blank String |
| `version` | `PluginDescriptor.version` | Optional String |
| `url` | `PluginDescriptor.url` | Optional; if present must start with `http://` or `https://` |
| `description` | `PluginDescriptor.description` | Optional String |
| `extensionPoints` | Returned in `PluginResponse` | Optional List of objects |

Additional manifest keys are stored but not validated.

**Source**: `PluginDescriptorService.java:22-54`

### Extension Points

`PluginResponse.from()` reads `manifest["extensionPoints"]` as a `List<Map<String, Object>>` and exposes it in the API response. The host does not enforce any schema for extension point objects — the tests use `{"type": "product-tab", "label": "Reviews"}` as an example.

**Source**: `PluginResponse.java:18-36`, `PluginRegistryIntegrationTests.java:46-50`

---

## 2. PluginDescriptorRepository (`PluginDescriptorRepository.java`)

Extends `JpaRepository<PluginDescriptor, String>`.

### Custom Query Methods

- `findByEnabledTrue()` — used by `GET /api/plugins` to return only active plugins.

No other custom queries. Standard JPA `findById`, `save`, `delete` are used directly.

**Source**: `PluginDescriptorRepository.java:1-10`

---

## 3. PluginDescriptorService (`PluginDescriptorService.java`)

Central service guarding all plugin operations. Every plugin data/object operation calls `findEnabledOrThrow(pluginId)` first — disabled or non-existent plugins get a 404.

### Plugin ID constraint

```java
private static final String PLUGIN_ID_PATTERN = "^[a-zA-Z0-9_-]+$";
```

Plugin IDs may only contain alphanumeric characters, underscores, and hyphens.

**Source**: `PluginDescriptorService.java:19`

### Available Operations

| Method | Description |
|--------|-------------|
| `uploadManifest(pluginId, manifest)` | Upsert: creates if absent, updates if exists |
| `findAllEnabled()` | Returns only enabled plugins |
| `findById(pluginId)` | Returns any plugin (enabled or not); throws 404 if absent |
| `findEnabledOrThrow(pluginId)` | Returns only if enabled; throws 404 otherwise |
| `delete(pluginId)` | Hard delete |
| `setEnabled(pluginId, enabled)` | Toggle enabled/disabled flag |

**Source**: `PluginDescriptorService.java:21-88`

---

## 4. PluginController REST API (`PluginController.java`)

**Base path**: `/api/plugins`

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `PUT` | `/{pluginId}/manifest` | Register or update manifest | `PluginResponse` |
| `GET` | `/` | List all enabled plugins | `List<PluginResponse>` |
| `GET` | `/{pluginId}` | Get single plugin (any state) | `PluginResponse` |
| `DELETE` | `/{pluginId}` | Hard delete plugin | 204 No Content |
| `PATCH` | `/{pluginId}/enabled` | Enable/disable plugin | `PluginResponse` |

**Source**: `PluginController.java:1-61`

---

## 5. PluginObject Entity (`PluginObject.java`)

**Table**: `plugin_objects`
**Primary key**: `BIGINT id` — generated from sequence `plugin_object_seq` (allocationSize=1), inherits from `BaseEntity`.

### Columns

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `id` | BIGINT | NO | Sequence PK from `BaseEntity` |
| `plugin_id` | VARCHAR(255) | NO | FK to `plugins.id` |
| `object_type` | VARCHAR(255) | NO | Plugin-defined type string (e.g. "review", "comment") |
| `object_id` | VARCHAR(255) | NO | Plugin-defined object identifier |
| `data` | JSONB | NO | Arbitrary plugin payload |
| `entity_type` | VARCHAR(50) | YES | Enum string: `PRODUCT` or `CATEGORY` |
| `entity_id` | BIGINT | YES | ID of the host entity being referenced |
| `created_at` | TIMESTAMP | NO | From `BaseEntity` / JPA Auditing |
| `updated_at` | TIMESTAMP | NO | `@Version` from `BaseEntity` |

**Unique constraint**: `(plugin_id, object_type, object_id)` — named `uk_plugin_objects_plugin_type_id`

**Source**: `PluginObject.java:1-64`, migration `006-create-plugin-objects-table.yaml`, `007-add-entity-binding-to-plugin-objects.yaml`

### Business Key

`equals()` and `hashCode()` are based on `(pluginId, objectType, objectId)` — the unique natural key, not the database `id`.

**Source**: `PluginObject.java:52-63`

### Indexes

| Index | Columns | Purpose |
|-------|---------|---------|
| `idx_plugin_objects_plugin_id` | `plugin_id` | Basic plugin lookup |
| `idx_plugin_objects_plugin_type` | `plugin_id, object_type` | List by type |
| `idx_plugin_objects_entity_binding` | `plugin_id, object_type, entity_type, entity_id` | Entity-scoped list by type |
| `idx_plugin_objects_entity_cross_type` | `plugin_id, entity_type, entity_id` | Cross-type entity listing |

**Source**: migrations `006-create-plugin-objects-table.yaml:67-80`, `007-add-entity-binding-to-plugin-objects.yaml:14-37`

---

## 6. EntityType Enum (`EntityType.java`)

```java
public enum EntityType {
    PRODUCT,
    CATEGORY
}
```

Only two values. Plugins can bind objects to products or categories. There is no current mechanism for plugins to extend this enum.

**Source**: `EntityType.java:1-6`

---

## 7. PluginObjectRepository (`PluginObjectRepository.java`)

Extends `JpaRepository<PluginObject, Long>`.

### Custom Query Methods

| Method | Signature | Use |
|--------|-----------|-----|
| `findByPluginIdAndObjectType` | `(String, String) → List<PluginObject>` | List by plugin+type (not used in service layer — service delegates to jOOQ) |
| `findByPluginIdAndObjectTypeAndObjectId` | `(String, String, String) → Optional<PluginObject>` | Get/upsert single object |
| `deleteByPluginIdAndObjectTypeAndObjectId` | `(String, String, String) → void` | Not used in service; service uses `delete(entity)` instead |

**Note**: The list operations in the service layer do NOT use the JPA repository directly — they use `DbPluginObjectQueryService` (jOOQ) to support JSONB filtering. The JPA repository is only used for single-object lookups and persistence.

**Source**: `PluginObjectRepository.java:1-15`

---

## 8. PluginObjectService (`PluginObjectService.java`)

All methods call `pluginDescriptorService.findEnabledOrThrow(pluginId)` as the first step.

### Available Operations

| Method | Parameters | Description |
|--------|------------|-------------|
| `list(pluginId, objectType)` | String, String | All objects of a type; no entity filter, limit=1000 |
| `list(pluginId, objectType, entityType, entityId)` | + EntityType, Long | Objects of a type bound to a specific entity; limit=1000 |
| `list(pluginId, objectType, entityType, entityId, filter, limit)` | full params | Full filter + pagination support |
| `listByEntity(pluginId, entityType, entityId)` | String, EntityType, Long | All object types for an entity; limit=1000 |
| `listByEntity(pluginId, entityType, entityId, filter)` | + String | With JSONB filter; limit=1000 |
| `listByEntity(pluginId, entityType, entityId, filter, limit)` | full params | Full filter + pagination |
| `get(pluginId, objectType, objectId)` | String, String, String | Single object by natural key; throws 404 if absent |
| `save(pluginId, objectType, objectId, data)` | String×3, Map | Upsert without entity binding |
| `save(pluginId, objectType, objectId, data, entityType, entityId)` | + EntityType, Long | Upsert with entity binding |
| `delete(pluginId, objectType, objectId)` | String×3 | Delete by natural key; throws 404 if absent |

**Upsert semantics**: `save()` does a find-or-create on `(pluginId, objectType, objectId)`, then overwrites `data`, `entityType`, and `entityId`. Passing `null` for entity fields clears any previous binding.

**Source**: `PluginObjectService.java:1-95`

---

## 9. DbPluginObjectQueryService (`DbPluginObjectQueryService.java`)

jOOQ-based query service (follows `Db*QueryService` naming convention per project standards).

### Method

```java
findByFilter(String pluginId, String objectType, EntityType entityType, Long entityId, String filter, int limit)
    → List<PluginObjectResponse>
```

Conditions applied (all AND-combined):

1. `plugin_id = :pluginId` (always)
2. `object_type = :objectType` (if objectType not null)
3. `entity_type = :entityType` (if entityType not null)
4. `entity_id = :entityId` (if entityId not null)
5. JSONB filter condition (if filter not null)
6. `LIMIT :limit`

Passing `objectType = null` performs a cross-type query (all object types for the entity).

**Source**: `DbPluginObjectQueryService.java:33-89`

### JSONB Filter Syntax for Plugin Objects

Filter string format: `{jsonPath}:{operator}` or `{jsonPath}:{operator}:{value}`

```
filter=quantity:gt:0
filter=status:eq:active
filter=active:bool:true
filter=color:exists
filter=price:lt:100
```

#### jsonPath constraints

- Allowed characters: `[a-zA-Z0-9_.-]+` only (validated by regex)
- Single-level key access only — targets top-level keys in the `data` JSONB column
- No nested path support (e.g. `nested.key` would match a literal key named `nested.key`, not a nested object — the `->>` operator does not interpret dots as path separators)

**Source**: `DbPluginObjectQueryService.java:101-103`

#### Supported Operators

| Operator | Format | SQL Translation | Notes |
|----------|--------|-----------------|-------|
| `eq` | `key:eq:value` | `data->>'key' = 'value'` (bind param) | String equality |
| `gt` | `key:gt:number` | `CAST(data->>'key' AS double) > number` | Numeric; value must parse as double |
| `lt` | `key:lt:number` | `CAST(data->>'key' AS double) < number` | Numeric; value must parse as double |
| `exists` | `key:exists` | `jsonb_exists(data, 'key')` (bind param) | Key presence check; no value needed |
| `bool` | `key:bool:true|false` | `CAST(data->>'key' AS boolean) = value` | Boolean; uses `Boolean.parseBoolean` |

**Source**: `DbPluginObjectQueryService.java:115-134`

#### Limitation: single filter only

The `filter` parameter is a single String. The API accepts only one JSONB filter per request on the plugin objects endpoints. Multiple filters are not supported here (contrast with product `pluginFilter` which is a multi-value param).

---

## 10. PluginObjectController REST API (`PluginObjectController.java`)

**Base path**: `/api/plugins/{pluginId}/objects`

### Endpoints

#### `GET /api/plugins/{pluginId}/objects`

Cross-type listing. Returns all object types for a given entity.

- `entityType` (required) — `PRODUCT` or `CATEGORY`
- `entityId` (required) — Long
- `filter` (optional) — JSONB filter string
- `limit` (optional, default=1000, max=1000)

Both `entityType` and `entityId` must be present or the request returns 400.

**Source**: `PluginObjectController.java:29-40`

#### `GET /api/plugins/{pluginId}/objects/{objectType}`

List objects of a specific type. Supports optional entity scoping and JSONB filter.

- `entityType` (optional) — must be paired with `entityId`
- `entityId` (optional) — must be paired with `entityType`
- `filter` (optional) — JSONB filter string
- `limit` (optional, default=1000, max=1000)

If only one of `entityType`/`entityId` is provided → 400.

**Source**: `PluginObjectController.java:42-54`

#### `GET /api/plugins/{pluginId}/objects/{objectType}/{objectId}`

Get single object by natural key. Returns 404 if not found.

**Source**: `PluginObjectController.java:56-62`

#### `PUT /api/plugins/{pluginId}/objects/{objectType}/{objectId}`

Upsert. Request body is the full `data` Map (JSON object). Optionally accepts entity binding via query params.

- `entityType` (optional query param)
- `entityId` (optional query param)

Both must be provided together or both absent. Returns updated `PluginObjectResponse`.

**Source**: `PluginObjectController.java:64-76`

#### `DELETE /api/plugins/{pluginId}/objects/{objectType}/{objectId}`

Delete single object by natural key. Returns 204. Throws 404 if not found.

**Source**: `PluginObjectController.java:78-85`

### Response shape (`PluginObjectResponse`)

```java
record PluginObjectResponse(
    Long id,
    String pluginId,
    String objectType,
    String objectId,
    Map<String, Object> data,
    EntityType entityType,
    Long entityId,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
)
```

**Source**: `PluginObjectResponse.java:6-16`

---

## 11. PluginDataService (`PluginDataService.java`)

This is a completely separate mechanism from `PluginObject`. It embeds plugin data as a namespaced key inside the host entity's `pluginData` JSONB column.

**Currently scoped to Products only.** The service has a hard dependency on `ProductRepository` — there is no category equivalent.

### Storage Model

`products.plugin_data` is a JSONB column. Its structure is:

```json
{
  "reviews": { "rating": 4.5, "count": 10 },
  "seo":     { "title": "Best Phone" }
}
```

Each plugin writes to its own namespace (keyed by `pluginId`). Plugins cannot read or overwrite each other's namespace through this API (the service reads/writes only the `pluginId` subkey).

**Source**: `PluginDataService.java:23-55`, `Product.java:49-51`

### Available Operations

| Method | Description |
|--------|-------------|
| `getData(pluginId, productId)` | Returns the plugin's data map for a product; returns `Map.of()` if none |
| `setData(pluginId, productId, data)` | Writes/replaces the plugin's entire data map for a product |
| `removeData(pluginId, productId)` | Removes only the plugin's namespace from the product's JSONB |

All methods call `findEnabledOrThrow(pluginId)` first. `removeData` is a no-op if `pluginData` is already null.

**Source**: `PluginDataService.java:23-70`

### Database detail

The `products.plugin_data` column has a GIN index:
```sql
CREATE INDEX idx_products_plugin_data_gin ON products USING gin (plugin_data)
```

**Source**: migration `005-add-plugin-data-to-products.yaml:13`

---

## 12. PluginDataController REST API (`PluginDataController.java`)

**Base path**: `/api/plugins/{pluginId}/products/{productId}/data`

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | (base) | Get plugin's data for product | `Map<String, Object>` |
| `PUT` | (base) | Set plugin's data for product (full replace) | `Map<String, Object>` (echo) |
| `DELETE` | (base) | Remove plugin's data namespace from product | 204 No Content |

**Source**: `PluginDataController.java:1-44`

---

## 13. Product pluginFilter — Cross-Cutting Feature

The product listing endpoint exposes plugin data filtering, allowing callers to query products by their embedded plugin data.

**Endpoint**: `GET /api/products?pluginFilter=...`

Filter format: `{pluginId}:{jsonPath}:{operator}:{value}` or `{pluginId}:{jsonPath}:exists`

```
pluginFilter=reviews:rating:gt:3
pluginFilter=seo:title:exists
```

Multiple `pluginFilter` params are accepted and combined as AND conditions.

SQL generated (example for `reviews:rating:gt:3`):
```sql
CAST(plugin_data->'reviews'->>'rating' AS double) > 3
```

For `exists`:
```sql
jsonb_exists(plugin_data->'seo', 'title')
```

The operator set is identical to `DbPluginObjectQueryService.parseFilter`: `eq`, `gt`, `lt`, `exists`, `bool`.

**Source**: `DbProductQueryService.java:121-169`, `ProductController.java:29-35`, `PluginGapTests.java:74-183`

---

## 14. Behaviour Guarantees and Constraints

### Upsert semantics (PluginObject)

`save()` finds by natural key `(pluginId, objectType, objectId)`. If found, it overwrites `data`, `entityType`, and `entityId` — including setting them to null. There is no partial update.

**Source**: `PluginObjectService.java:66-86`

### Entity binding is optional and nullable

Both `entityType` and `entityId` can be null. They can also be explicitly cleared by calling `save()` with null values. The unique constraint covers only `(plugin_id, object_type, object_id)` — there can be multiple objects with the same `(entityType, entityId)` combination.

**Source**: `PluginObject.java:44-49`, `PluginObjectEntityBindingTests.java:65-75`

### Plugin isolation

- Plugin objects: `plugin_id` is always part of every query condition. Plugins cannot query each other's objects.
- Plugin data (embedded): the service reads and writes only `pluginData[pluginId]`. A plugin cannot read another plugin's namespace. The full `pluginData` map is visible in `GET /api/products/{id}` and `GET /api/products` responses.

### Disabled plugin → 404

Any attempt to access plugin objects or plugin data for a disabled plugin returns 404, as if the plugin doesn't exist.

**Source**: `PluginDescriptorService.java:68-73`, `PluginGapTests.java:211-218`

### Limit cap

The controller caps `limit` at 1000 regardless of what the caller passes. There is no pagination/offset support — only a hard limit.

**Source**: `PluginObjectController.java:27`, `PluginObjectController.java:39,53`

### No cascade delete

Deleting a plugin (`DELETE /api/plugins/{pluginId}`) does NOT cascade to `plugin_objects`. The FK `fk_plugin_objects_plugin_id` has no `ON DELETE CASCADE`. Orphaned rows will remain in the table.

**Source**: migration `006-create-plugin-objects-table.yaml:57-63` — no cascade clause present.

### PluginData is Product-only

`PluginDataService` is hard-coded to `ProductRepository`. There is no equivalent service for `Category`. The `EntityType.CATEGORY` value exists only for `PluginObject` entity binding.

**Source**: `PluginDataService.java:14-18`

---

## 15. Summary: What a Plugin Can Do

### Via PluginObject API (standalone objects)

1. Register itself (`PUT /api/plugins/{pluginId}/manifest`)
2. Store arbitrary JSONB objects with a 3-part natural key: `(pluginId, objectType, objectId)`
3. Optionally bind objects to a PRODUCT or CATEGORY entity
4. Query objects by type, by entity binding, by JSONB field value (5 operators)
5. Query all object types bound to a given entity (cross-type)
6. Update objects (full replace of `data` and entity binding)
7. Delete individual objects

### Via PluginData API (embedded in host entity)

8. Read its own namespaced JSONB data from a product
9. Write (full replace) its own namespaced JSONB data on a product
10. Remove its own namespace from a product

### Via Product List API (read-only, query-side)

11. Products can be filtered by plugin-embedded data using `pluginFilter` query params

### What a plugin CANNOT do (limitations)

- Query or modify another plugin's data (enforced by pluginId scoping)
- Store data on categories via PluginData (Product-only)
- Paginate or sort plugin objects (limit-only, max 1000)
- Use nested JSONB paths in filter expressions
- Apply multiple JSONB filters in a single plugin object list request
- Extend `EntityType` without changing the enum
- Avoid orphaned plugin_objects rows when the plugin is deleted
