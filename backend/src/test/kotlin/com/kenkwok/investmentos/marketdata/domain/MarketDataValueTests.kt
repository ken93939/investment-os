package com.kenkwok.investmentos.marketdata.domain

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketDataValueTests {
    @Test
    fun `GIVEN invalid domain values WHEN constructing them THEN domain validation hides untrusted input`() {
        // GIVEN
        val secret = "secret?apikey=untrusted"
        val end = LocalDate.parse("2026-01-01")

        // WHEN
        val results = listOf(runCatching { Symbol.parse(secret) }, runCatching { DateRange(end.plusDays(1), end) })

        // THEN
        results.forEach { result ->
            val error = assertIs<InvalidMarketDataValue>(result.exceptionOrNull())
            assertTrue(!error.message.orEmpty().contains(secret))
            assertNull(error.cause)
        }
    }
}
