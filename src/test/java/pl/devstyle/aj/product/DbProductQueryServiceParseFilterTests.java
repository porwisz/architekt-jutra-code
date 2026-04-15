package pl.devstyle.aj.product;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DbProductQueryServiceParseFilterTests {

    @Test
    void parseFilter_gteOperator_returnsNonNullCondition() {
        var condition = DbProductQueryService.parseFilter("reviews:rating:gte:4");
        assertNotNull(condition);
    }

    @Test
    void parseFilter_gteOperator_nonNumericValue_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                DbProductQueryService.parseFilter("reviews:rating:gte:abc"));
    }
}
