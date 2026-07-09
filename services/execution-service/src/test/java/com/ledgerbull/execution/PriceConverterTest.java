package com.ledgerbull.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ledgerbull.execution.service.PriceConverter;
import org.junit.jupiter.api.Test;

class PriceConverterTest {

    @Test
    void convertsHumanToTicksAndBack() {
        assertEquals(10123L, PriceConverter.toTicks(101.23));
        assertEquals(101.23, PriceConverter.fromTicks(10123L), 0.0001);
        assertEquals(10500L, PriceConverter.toTicks(105.0));
        assertEquals(105.0, PriceConverter.fromTicks(10500L), 0.0001);
    }
}
