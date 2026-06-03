package pl.devstyle.aj.footprint.api.exception;

import java.util.Map;

public final class MissingProductAttributeException extends FootprintCalculationException {

    public static final String CODE = "MISSING_PRODUCT_ATTRIBUTE";

    private final String productId;
    private final String attribute;

    public MissingProductAttributeException(String productId, String attribute) {
        super("Missing attribute '" + attribute + "' on product " + productId);
        this.productId = productId;
        this.attribute = attribute;
    }

    public String productId() {
        return productId;
    }

    public String attribute() {
        return attribute;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of(
                "productId", productId,
                "attribute", attribute
        );
    }
}
