package pl.devstyle.aj.archetype.pricing;

public record CalculatorId(String value) {

    public CalculatorId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CalculatorId value must not be blank");
        }
    }
}
