package pl.devstyle.aj.archetype.pricing;

public record ComponentId(String value) {

    public ComponentId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ComponentId value must not be blank");
        }
    }
}
