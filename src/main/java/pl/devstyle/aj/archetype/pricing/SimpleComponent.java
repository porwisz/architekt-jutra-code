package pl.devstyle.aj.archetype.pricing;

public record SimpleComponent(
        ComponentId id,
        CalculatorId calculatorId,
        QuantityExtractor extractor,
        Applicability applicability,
        int scope
) implements Component {
}
