package com.ledgerbull.position.money;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MoneyTest {

    @Test
    void multiplyQtyPrice_exactInteger() {
        assertEquals(10000L, Money.multiplyQtyPrice(5L, 2000L));
        assertEquals(3000L, Money.multiplyQtyPrice(3L, 1000L));
        assertEquals(13000L, Money.multiplyQtyPrice(5L, 2000L) + Money.multiplyQtyPrice(3L, 1000L));
    }

    @Test
    void toHuman_formatsTicks() {
        assertEquals("130.00", Money.toHuman(13000L));
        assertEquals("100.00", Money.toHuman(10000L));
    }
}
