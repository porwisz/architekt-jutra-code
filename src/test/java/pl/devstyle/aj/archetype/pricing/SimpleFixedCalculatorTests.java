package pl.devstyle.aj.archetype.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SimpleFixedCalculatorTests {

    private final SimpleFixedCalculator calculator = new SimpleFixedCalculator();

    @Test
    void calculate_rateTimesQuantity_roundsHalfUpAt4Decimals() {
        var rate = new BigDecimal("0.123456");
        var quantity = new BigDecimal("2.5");

        var result = calculator.calculate(rate, quantity);

        assertThat(result).isEqualByComparingTo("0.3086");
        assertThat(result.scale()).isEqualTo(4);
    }

    @Test
    void calculate_zeroQuantity_returnsZero() {
        var rate = new BigDecimal("1.2345");
        var quantity = BigDecimal.ZERO;

        var result = calculator.calculate(rate, quantity);

        assertThat(result).isEqualByComparingTo("0.0000");
        assertThat(result.scale()).isEqualTo(4);
    }
}
