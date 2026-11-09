package com.wizytowki.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun localDateTime_roundTrip() {
        val value = LocalDateTime.of(2026, 11, 9, 18, 30, 15, 123_000_000)
        val stored = converters.toLocalDateTime(value)
        assertEquals("2026-11-09T18:30:15.123", stored)
        assertEquals(value, converters.fromLocalDateTime(stored))
    }

    @Test
    fun localDateTime_null() {
        assertNull(converters.toLocalDateTime(null))
        assertNull(converters.fromLocalDateTime(null))
    }
}
