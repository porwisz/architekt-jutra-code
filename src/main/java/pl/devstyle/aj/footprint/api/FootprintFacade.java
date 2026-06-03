package pl.devstyle.aj.footprint.api;

public interface FootprintFacade {
    FootprintBreakdown calculateTotal(FootprintParameters params, CalculationOptions options);

    FootprintBreakdown calculateUnit(FootprintParameters params, CalculationOptions options);
}
