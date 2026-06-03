package pl.devstyle.aj.footprint.export;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.devstyle.aj.footprint.api.exception.InvalidParametersException;
import pl.devstyle.aj.footprint.audit.FootprintAuditEntity;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Flattens the JSON breakdown stored on an audit row into one CSV row per ACTIVE leaf.
 * Root is excluded from {@code component_path}; the path is a dot-delimited chain of child
 * componentIds (e.g. {@code materials.raw-material}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BreakdownCsvFlattener {

    /** Defensive cap — V1 tree yields at most 9 leaves. Any deeper tree is rejected as malformed. */
    static final int MAX_LEAF_ROWS = 1000;

    private final ObjectMapper objectMapper;

    public List<List<String>> flatten(FootprintAuditEntity row) {
        var rows = new ArrayList<List<String>>();
        Map<String, Object> wrapper = row.getBreakdown();
        if (wrapper == null) {
            return rows;
        }
        Object rootObj = wrapper.get("root");
        if (!(rootObj instanceof Map<?, ?> rootMap)) {
            return rows;
        }
        String correlationId = row.getCorrelationId().toString();
        String computedAt = row.getRequestedAt().toString();
        String productId = row.getProductId();
        String timestampParam = extractTimestampParam(wrapper, computedAt);
        walk(asStringObjectMap(rootMap), "", correlationId, computedAt, productId, timestampParam, rows);
        if (rows.size() > MAX_LEAF_ROWS) {
            throw new InvalidParametersException("breakdown.leafCount", rows.size());
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static String extractTimestampParam(Map<String, Object> wrapper, String fallback) {
        Object parametersEcho = wrapper.get("parametersEcho");
        if (parametersEcho instanceof Map<?, ?> echo) {
            Object ts = echo.get("timestamp");
            if (ts != null) {
                return ts.toString();
            }
        }
        Object computedAt = wrapper.get("computedAt");
        return computedAt != null ? computedAt.toString() : fallback;
    }

    private void walk(Map<String, Object> node, String pathPrefix,
                      String correlationId, String computedAt, String productId, String timestampParam,
                      List<List<String>> rows) {
        Object children = node.get("children");
        String componentId = componentId(node);
        if (children instanceof List<?> childList && !childList.isEmpty()) {
            for (Object child : childList) {
                if (child instanceof Map<?, ?> childMap) {
                    Map<String, Object> childNode = asStringObjectMap(childMap);
                    String childId = componentId(childNode);
                    String nextPath = pathPrefix.isEmpty() ? childId : pathPrefix + "." + childId;
                    walk(childNode, nextPath, correlationId, computedAt, productId, timestampParam, rows);
                }
            }
            return;
        }
        // leaf
        String componentPath = pathPrefix.isEmpty() ? componentId : pathPrefix;
        rows.add(buildLeafRow(node, componentId, componentPath, correlationId, computedAt, productId, timestampParam));
    }

    private List<String> buildLeafRow(Map<String, Object> leaf,
                                      String componentId,
                                      String componentPath,
                                      String correlationId,
                                      String computedAt,
                                      String productId,
                                      String timestampParam) {
        var row = new ArrayList<String>(12);
        row.add(correlationId);
        row.add(computedAt);
        row.add(productId);
        row.add(timestampParam);
        row.add(componentPath);
        row.add(componentId);
        row.add(formatKgCo2(leaf.get("kgCo2")));
        row.add(stringify(leaf.get("scope")));
        row.add(formatBigDecimal(leaf.get("factorRate")));
        row.add(stringify(leaf.get("factorValidFrom")));
        row.add(stringify(leaf.get("factorVersionId")));
        row.add(formatWarnings(leaf.get("warnings")));
        return row;
    }

    private static String componentId(Map<String, Object> node) {
        Object raw = node.get("componentId");
        if (raw instanceof Map<?, ?> m) {
            Object v = m.get("value");
            return v != null ? v.toString() : "";
        }
        return raw != null ? raw.toString() : "";
    }

    private static String formatKgCo2(Object value) {
        return toBigDecimal(value).setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatBigDecimal(Object value) {
        if (value == null) {
            return "";
        }
        return toBigDecimal(value).toPlainString();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number n) {
            return new BigDecimal(n.toString());
        }
        return new BigDecimal(value.toString());
    }

    private static String stringify(Object value) {
        return value == null ? "" : value.toString();
    }

    private String formatWarnings(Object warnings) {
        if (warnings == null) {
            return "";
        }
        if (warnings instanceof List<?> list && list.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(warnings);
        } catch (JacksonException e) {
            log.warn("Failed to serialise CSV warnings cell for export — returning empty string", e);
            return "";
        }
    }
}
