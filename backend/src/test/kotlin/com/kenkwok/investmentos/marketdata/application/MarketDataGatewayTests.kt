package com.kenkwok.investmentos.marketdata.application

import com.kenkwok.investmentos.marketdata.application.port.out.FinancialDataProvider
import com.kenkwok.investmentos.marketdata.domain.AssetType
import com.kenkwok.investmentos.marketdata.domain.DataFreshness
import com.kenkwok.investmentos.marketdata.domain.DateRange
import com.kenkwok.investmentos.marketdata.domain.FinancialStatements
import com.kenkwok.investmentos.marketdata.domain.Instrument
import com.kenkwok.investmentos.marketdata.domain.MacroSeries
import com.kenkwok.investmentos.marketdata.domain.MacroSeriesId
import com.kenkwok.investmentos.marketdata.domain.PriceAdjustment
import com.kenkwok.investmentos.marketdata.domain.PriceHistory
import com.kenkwok.investmentos.marketdata.domain.Provenance
import com.kenkwok.investmentos.marketdata.domain.ReportPeriod
import com.kenkwok.investmentos.marketdata.domain.Sourced
import com.kenkwok.investmentos.marketdata.domain.StatementType
import com.kenkwok.investmentos.marketdata.domain.Symbol
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class MarketDataGatewayTests {
    @Test
    fun `GIVEN duplicate provider IDs WHEN building the gateway THEN ambiguous configuration is rejected`() {
        // GIVEN
        val providers = listOf(TestProvider("first"), TestProvider("first"))

        // WHEN
        val result = runCatching { MarketDataGateway("first", providers) }

        // THEN
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    private class TestProvider(
        override val id: String,
    ) : FinancialDataProvider {
        var requestedSymbol: Symbol? = null
        var requestedQuery: String? = null
        var failure: MarketDataException? = null

        override fun instrument(symbol: Symbol): Sourced<Instrument> {
            failure?.let { throw it }
            requestedSymbol = symbol
            return Sourced(
                Instrument(symbol, "Test company", AssetType.STOCK, Currency.getInstance("USD"), null, null, "US"),
                Provenance(id, "test-only", Instant.parse("2026-01-01T00:00:00Z"), DataFreshness.UNKNOWN),
            )
        }

        override fun search(query: String): Sourced<List<Instrument>> {
            requestedQuery = query
            val result = instrument(Symbol.parse("IBM"))
            return Sourced(listOf(result.data), result.provenance)
        }

        override fun prices(
            symbol: Symbol,
            range: DateRange,
            adjustment: PriceAdjustment,
        ): Sourced<PriceHistory> = throw UnsupportedOperationException("Unused test capability")

        override fun statements(
            symbol: Symbol,
            type: StatementType,
            period: ReportPeriod,
        ): Sourced<FinancialStatements> = throw UnsupportedOperationException("Unused test capability")

        override fun macroSeries(
            series: MacroSeriesId,
            range: DateRange,
        ): Sourced<MacroSeries> = throw UnsupportedOperationException("Unused test capability")
    }

    @Test
    fun `GIVEN a symbol with whitespace and lowercase WHEN requesting metadata THEN the provider receives its canonical symbol`() {
        // GIVEN
        val provider = TestProvider("primary")
        val gateway = MarketDataGateway("primary", listOf(provider))

        // WHEN
        val result = gateway.instrument("  brk.b  ")

        // THEN
        assertEquals("BRK.B", provider.requestedSymbol?.value)
        assertEquals("BRK.B", result.data.symbol.value)
        assertEquals("primary", result.provenance.provider)
        assertNull(result.provenance.sourceAsOf)
    }

    @Test
    fun `GIVEN a Turkish default locale WHEN normalizing a symbol THEN normalization uses locale independent uppercase`() {
        // GIVEN
        val original = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            // WHEN
            val symbol = Symbol.parse("ibm")

            // THEN
            assertEquals("IBM", symbol.value)
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `GIVEN malformed symbols WHEN requesting metadata THEN input is rejected before provider access`() {
        // GIVEN
        val provider = TestProvider("primary")
        val gateway = MarketDataGateway("primary", listOf(provider))

        listOf("", "BRK..B", "AAPL?apikey=secret", "A/B", "A".repeat(21)).forEach { input ->
            // WHEN
            val result = runCatching { gateway.instrument(input) }

            // THEN
            assertEquals(MarketDataError.INVALID_INPUT, assertIs<MarketDataException>(result.exceptionOrNull()).code)
            assertNull(provider.requestedSymbol)
        }
    }

    @Test
    fun `GIVEN an inverted date range WHEN requesting prices THEN input is rejected before provider access`() {
        // GIVEN
        val provider = TestProvider("primary")
        val gateway = MarketDataGateway("primary", listOf(provider))

        // WHEN
        val result = runCatching { gateway.prices("IBM", LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-01")) }

        // THEN
        assertEquals(MarketDataError.INVALID_INPUT, assertIs<MarketDataException>(result.exceptionOrNull()).code)
    }

    @Test
    fun `GIVEN two available providers WHEN selecting another primary THEN callers still receive the same application models`() {
        // GIVEN
        val first = TestProvider("first")
        val second = TestProvider("second")
        val gateway = MarketDataGateway("second", listOf(first, second))

        // WHEN
        val result = gateway.instrument("IBM")

        // THEN
        assertEquals("second", result.provenance.provider)
        assertNull(first.requestedSymbol)
        assertEquals("IBM", second.requestedSymbol?.value)
    }

    @Test
    fun `GIVEN no configured provider WHEN requesting data THEN a typed configuration error is returned`() {
        // GIVEN
        val gateway = MarketDataGateway("none", emptyList())

        // WHEN
        val result = runCatching { gateway.instrument("IBM") }

        // THEN
        assertEquals(MarketDataError.NOT_CONFIGURED, assertIs<MarketDataException>(result.exceptionOrNull()).code)
    }

    @Test
    fun `GIVEN an unknown configured provider WHEN building the gateway THEN configuration fails immediately`() {
        // GIVEN
        val providers = listOf(TestProvider("first"))

        // WHEN
        val result = runCatching { MarketDataGateway("missing", providers) }

        // THEN
        assertIs<IllegalArgumentException>(result.exceptionOrNull())
    }

    @Test
    fun `GIVEN a rate limited provider WHEN requesting metadata THEN its typed error is preserved without fallback`() {
        // GIVEN
        val first = TestProvider("first").apply { failure = MarketDataException(MarketDataError.RATE_LIMITED, "Rate limited", id) }
        val second = TestProvider("second")
        val gateway = MarketDataGateway("first", listOf(first, second))

        // WHEN
        val result = runCatching { gateway.instrument("IBM") }

        // THEN
        assertEquals(MarketDataError.RATE_LIMITED, assertIs<MarketDataException>(result.exceptionOrNull()).code)
        assertNull(second.requestedSymbol)
    }

    @Test
    fun `GIVEN a search with surrounding spaces WHEN searching THEN trimmed text reaches the provider`() {
        // GIVEN
        val provider = TestProvider("primary")
        val gateway = MarketDataGateway("primary", listOf(provider))

        // WHEN
        gateway.search("  International Business Machines  ")

        // THEN
        assertEquals("International Business Machines", provider.requestedQuery)
    }

    @Test
    fun `GIVEN empty or control character search text WHEN searching THEN input is rejected before provider access`() {
        // GIVEN
        val provider = TestProvider("primary")
        val gateway = MarketDataGateway("primary", listOf(provider))

        listOf("  ", "A\nB", "x".repeat(101)).forEach { input ->
            // WHEN
            val result = runCatching { gateway.search(input) }

            // THEN
            assertEquals(MarketDataError.INVALID_INPUT, assertIs<MarketDataException>(result.exceptionOrNull()).code)
            assertNull(provider.requestedQuery)
        }
    }
}
