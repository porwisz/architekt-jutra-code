package pl.devstyle.aj.mcp.security;

/**
 * ThreadLocal holder for Token-B (the exchanged backend-scoped token).
 * Set by tool handlers from McpTransportContext, read by RestClient interceptor.
 * Both run on the same thread (MCP SDK's boundedElastic), so ThreadLocal works.
 */
public class ExchangedTokenHolder {

    private static final ThreadLocal<String> TOKEN = new ThreadLocal<>();

    public static void set(String token) {
        TOKEN.set(token);
    }

    public static String get() {
        return TOKEN.get();
    }

    public static void clear() {
        TOKEN.remove();
    }
}
