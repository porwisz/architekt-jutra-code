package pl.devstyle.aj.product;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SortField;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.devstyle.aj.category.CategoryResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static pl.devstyle.aj.jooq.tables.Categories.CATEGORIES;
import static pl.devstyle.aj.jooq.tables.Products.PRODUCTS;

@Service
public class DbProductQueryService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "price", "sku", "createdAt");

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    public DbProductQueryService(DSLContext dsl, ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll(Long categoryId, String search, String sort, List<String> pluginFilters) {
        var conditions = new ArrayList<Condition>();

        if (categoryId != null) {
            conditions.add(PRODUCTS.CATEGORY_ID.eq(categoryId));
        }
        if (search != null && !search.isBlank()) {
            conditions.add(PRODUCTS.NAME.likeIgnoreCase("%" + search + "%"));
        }
        if (pluginFilters != null) {
            for (var filter : pluginFilters) {
                if (filter != null && !filter.isBlank()) {
                    conditions.add(parseFilter(filter));
                }
            }
        }

        var sortField = parseSortField(sort);

        return dsl.select(
                        PRODUCTS.ID, PRODUCTS.NAME, PRODUCTS.DESCRIPTION, PRODUCTS.PHOTO_URL,
                        PRODUCTS.PRICE, PRODUCTS.SKU, PRODUCTS.PLUGIN_DATA,
                        PRODUCTS.CREATED_AT, PRODUCTS.UPDATED_AT,
                        CATEGORIES.ID, CATEGORIES.NAME, CATEGORIES.DESCRIPTION,
                        CATEGORIES.CREATED_AT, CATEGORIES.UPDATED_AT)
                .from(PRODUCTS)
                .join(CATEGORIES).on(PRODUCTS.CATEGORY_ID.eq(CATEGORIES.ID))
                .where(conditions)
                .orderBy(sortField)
                .fetch(r -> {
                    var category = new CategoryResponse(
                            r.get(CATEGORIES.ID),
                            r.get(CATEGORIES.NAME),
                            r.get(CATEGORIES.DESCRIPTION),
                            r.get(CATEGORIES.CREATED_AT),
                            r.get(CATEGORIES.UPDATED_AT)
                    );

                    Map<String, Object> pluginData = null;
                    var jsonb = r.get(PRODUCTS.PLUGIN_DATA);
                    if (jsonb != null) {
                        try {
                            pluginData = objectMapper.readValue(jsonb.data(), Map.class);
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to deserialize plugin_data", e);
                        }
                    }

                    return new ProductResponse(
                            r.get(PRODUCTS.ID),
                            r.get(PRODUCTS.NAME),
                            r.get(PRODUCTS.DESCRIPTION),
                            r.get(PRODUCTS.PHOTO_URL),
                            r.get(PRODUCTS.PRICE),
                            r.get(PRODUCTS.SKU),
                            category,
                            pluginData,
                            r.get(PRODUCTS.CREATED_AT),
                            r.get(PRODUCTS.UPDATED_AT)
                    );
                });
    }

    private SortField<?> parseSortField(String sort) {
        if (sort == null || sort.isBlank()) {
            return PRODUCTS.CREATED_AT.desc();
        }

        var parts = sort.split(",");
        var fieldName = parts[0];
        var ascending = parts.length < 2 || !"desc".equalsIgnoreCase(parts[1]);

        if (!ALLOWED_SORT_FIELDS.contains(fieldName)) {
            return PRODUCTS.CREATED_AT.desc();
        }

        var column = switch (fieldName) {
            case "createdAt" -> PRODUCTS.CREATED_AT;
            case "price" -> PRODUCTS.PRICE;
            case "name" -> PRODUCTS.NAME;
            case "sku" -> PRODUCTS.SKU;
            default -> PRODUCTS.CREATED_AT;
        };

        return ascending ? column.asc() : column.desc();
    }

    static Condition parseFilter(String filter) {
        var parts = filter.split(":", 4);
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid pluginFilter format. Expected: {pluginId}:{jsonPath}:{operator}:{value}");
        }

        var pluginId = parts[0];
        var jsonPath = parts[1];
        var op = parts[2];
        var val = parts.length > 3 ? parts[3] : null;

        if (!pluginId.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Invalid pluginId: must contain only alphanumeric characters, underscores, dots, or hyphens");
        }
        if (!jsonPath.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Invalid jsonPath: must contain only alphanumeric characters, underscores, dots, or hyphens");
        }
        if (!op.matches("eq|gte|gt|lt|exists|bool")) {
            throw new IllegalArgumentException("Unsupported operator: " + op + ". Supported: eq, gte, gt, lt, exists, bool");
        }
        if (val == null && !op.equals("exists")) {
            throw new IllegalArgumentException("Operator '" + op + "' requires a value");
        }

        // pluginId and jsonPath are validated by regex — safe to use in JSON path expressions.
        // All comparison values use jOOQ bind parameters.
        var jsonExtract = DSL.field("plugin_data->'" + pluginId + "'->>'" + jsonPath + "'", String.class);

        return switch (op) {
            case "eq" -> jsonExtract.eq(val);
            case "gte" -> {
                try {
                    yield jsonExtract.cast(Double.class).ge(Double.parseDouble(val));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Value must be numeric for 'gte' operator: " + val);
                }
            }
            case "gt" -> {
                try {
                    yield jsonExtract.cast(Double.class).gt(Double.parseDouble(val));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Value must be numeric for 'gt' operator: " + val);
                }
            }
            case "lt" -> {
                try {
                    yield jsonExtract.cast(Double.class).lt(Double.parseDouble(val));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Value must be numeric for 'lt' operator: " + val);
                }
            }
            case "exists" -> DSL.condition("jsonb_exists(plugin_data->?, ?)", pluginId, jsonPath);
            case "bool" -> jsonExtract.cast(Boolean.class).eq(Boolean.parseBoolean(val));
            default -> throw new IllegalArgumentException("Unsupported operator: " + op + ". Supported: eq, gte, gt, lt, exists, bool");
        };
    }
}
