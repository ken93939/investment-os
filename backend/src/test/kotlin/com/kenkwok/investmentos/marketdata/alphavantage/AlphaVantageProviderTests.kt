package com.kenkwok.investmentos.marketdata.alphavantage

import com.kenkwok.investmentos.marketdata.AssetType
import com.kenkwok.investmentos.marketdata.DataFreshness
import com.kenkwok.investmentos.marketdata.DateRange
import com.kenkwok.investmentos.marketdata.FinancialField
import com.kenkwok.investmentos.marketdata.MacroSeriesId
import com.kenkwok.investmentos.marketdata.MarketDataError
import com.kenkwok.investmentos.marketdata.MarketDataException
import com.kenkwok.investmentos.marketdata.PriceAdjustment
import com.kenkwok.investmentos.marketdata.ReportPeriod
import com.kenkwok.investmentos.marketdata.StatementType
import com.kenkwok.investmentos.marketdata.Symbol
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlphaVantageProviderTests {
    private val retrievedAt = Instant.parse("2026-10-18T01:00:00Z")
    private val clock = Clock.fixed(retrievedAt, ZoneOffset.UTC)
    private val symbol = Symbol.parse("IBM")
    private val range = DateRange(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-01-31"))
    private val mapper = JsonMapper.builder().build()

    private fun provider(body: String): AlphaVantageProvider = AlphaVantageProvider(AlphaVantageTransport { mapper.readTree(body) }, clock)

    @Test
    fun `GIVEN mixed region and asset search results WHEN searching THEN only sourced US stocks and ETFs are returned`() {
        // GIVEN
        val provider =
            provider(
                """{"bestMatches":[
              {"1. symbol":"IBM","2. name":"IBM","3. type":"Equity","4. region":"United States","7. timezone":"UTC-04","8. currency":"USD"},
              {"1. symbol":"SPY","2. name":"SPY","3. type":"ETF","4. region":"United States","8. currency":"USD"},
              {"1. symbol":"TSCO.LON","2. name":"Tesco","3. type":"Equity","4. region":"United Kingdom"},
              {"1. symbol":"FUND","2. name":"Fund","3. type":"Mutual Fund","4. region":"United States"}
            ]}""",
            )

        // WHEN
        val result = provider.search("test")

        // THEN
        assertEquals(listOf("IBM", "SPY"), result.data.map { it.symbol.value })
        assertEquals(AssetType.ETF, result.data.last().assetType)
        assertEquals(Currency.getInstance("USD"), result.data.first().currency)
        assertNull(result.data.first().exchange)
        assertEquals(retrievedAt, result.provenance.retrievedAt)
        assertEquals("alpha-vantage", result.provenance.provider)
    }

    @Test
    fun `GIVEN company metadata WHEN fetching an instrument THEN currencies and exchange metadata are normalized`() {
        // GIVEN
        val provider =
            provider(
                """{"Symbol":"IBM","Name":"IBM","AssetType":"Common Stock","Currency":"USD","Exchange":"NYSE","Country":"USA"}""",
            )

        // WHEN
        val result = provider.instrument(symbol)

        // THEN
        assertEquals("US", result.data.country)
        assertEquals("NYSE", result.data.exchange)
        assertEquals("America/New_York", result.data.timeZone?.id)
        assertEquals(DataFreshness.UNKNOWN, result.provenance.freshness)
        assertNull(result.provenance.sourceAsOf)
    }

    @Test
    fun `GIVEN daily bars in reverse order WHEN requesting a date window THEN exact decimals and chronological raw bars are returned`() {
        // GIVEN
        val provider =
            provider(
                """{"Meta Data":{"2. Symbol":"IBM","3. Last Refreshed":"2026-02-02","5. Time Zone":"US/Eastern"},
            "Time Series (Daily)":{
              "2026-02-02":{"1. open":"12","2. high":"13","3. low":"11","4. close":"12","5. volume":"100"},
              "2026-01-06":{"1. open":"10","2. high":"12","3. low":"9","4. close":"10.0002000","5. volume":"123"},
              "2026-01-05":{"1. open":"9","2. high":"10","3. low":"8","4. close":"9","5. volume":"100"}
            }}""",
            )

        // WHEN
        val result = provider.prices(symbol, range, PriceAdjustment.RAW)

        // THEN
        assertEquals(listOf("2026-01-05", "2026-01-06"), result.data.bars.map { it.date.toString() })
        assertEquals(
            BigDecimal("10.0002000"),
            result.data.bars
                .last()
                .close,
        )
        assertEquals(PriceAdjustment.RAW, result.data.adjustment)
        assertEquals(LocalDate.parse("2026-02-02"), result.data.availableRange?.to)
        assertEquals(LocalDate.parse("2026-02-02"), result.provenance.sourceDate)
        assertNull(result.provenance.sourceAsOf)
        assertNull(result.data.currency)
        assertNull(result.data.exchange)
        assertEquals(DataFreshness.HISTORICAL, result.provenance.freshness)
    }

    @Test
    fun `GIVEN an adjusted price request WHEN calling the raw daily adapter THEN the unsupported operation is rejected without a call`() {
        // GIVEN
        var called = false
        val provider =
            AlphaVantageProvider(
                AlphaVantageTransport {
                    called = true
                    mapper.readTree("{}")
                },
                clock,
            )

        // WHEN
        val result = runCatching { provider.prices(symbol, range, PriceAdjustment.SPLIT_AND_DIVIDEND_ADJUSTED) }

        // THEN
        assertEquals(MarketDataError.UNSUPPORTED, assertIs<MarketDataException>(result.exceptionOrNull()).code)
        assertEquals(false, called)
    }

    @Test
    fun `GIVEN unavailable financial fields WHEN normalizing income THEN amounts remain null and publication time remains unknown`() {
        // GIVEN
        val provider =
            provider(
                """{"symbol":"IBM","annualReports":[{"fiscalDateEnding":"2025-12-31","reportedCurrency":"USD",
            "totalRevenue":"123456789012345.12345","grossProfit":"None","netIncome":"-100"}]}""",
            )

        // WHEN
        val result = provider.statements(symbol, StatementType.INCOME, ReportPeriod.ANNUAL)

        // THEN
        val report = result.data.reports.single()
        assertEquals(BigDecimal("123456789012345.12345"), report.values[FinancialField.REVENUE])
        assertEquals(BigDecimal("-100"), report.values[FinancialField.NET_INCOME])
        assertNull(report.values[FinancialField.GROSS_PROFIT])
        assertNull(report.values[FinancialField.OPERATING_INCOME])
        assertEquals(LocalDate.parse("2025-12-31"), report.fiscalPeriodEnd)
        assertNull(report.publishedAt)
        assertNull(result.provenance.sourceAsOf)
    }

    @Test
    fun `GIVEN balance and cash flow reports WHEN selecting their capabilities THEN standard fields use the correct endpoints`() {
        // GIVEN
        val requested = mutableListOf<String>()
        val provider =
            AlphaVantageProvider(
                AlphaVantageTransport { parameters ->
                    requested += parameters.getValue("function")
                    mapper.readTree(
                        """{"symbol":"IBM","quarterlyReports":[{"fiscalDateEnding":"2026-03-31","reportedCurrency":"USD",
                    "totalAssets":"20","operatingCashflow":"5","capitalExpenditures":"2"}]}""",
                    )
                },
                clock,
            )

        // WHEN
        val balance = provider.statements(symbol, StatementType.BALANCE_SHEET, ReportPeriod.QUARTERLY)
        val cash = provider.statements(symbol, StatementType.CASH_FLOW, ReportPeriod.QUARTERLY)

        // THEN
        assertEquals(listOf("BALANCE_SHEET", "CASH_FLOW"), requested)
        assertEquals(
            BigDecimal("20"),
            balance.data.reports
                .single()
                .values[FinancialField.TOTAL_ASSETS],
        )
        assertEquals(
            BigDecimal("5"),
            cash.data.reports
                .single()
                .values[FinancialField.OPERATING_CASH_FLOW],
        )
        assertEquals(
            BigDecimal("2"),
            cash.data.reports
                .single()
                .values[FinancialField.CAPITAL_EXPENDITURE],
        )
    }

    @Test
    fun `GIVEN a macro series with missing observations WHEN querying a range THEN units and missing values are preserved`() {
        // GIVEN
        val provider =
            provider(
                """{"name":"US treasury yield","interval":"monthly","unit":"percent","data":[
            {"date":"2026-02-01","value":"5"},{"date":"2026-01-02","value":"."},{"date":"2026-01-01","value":"4.25"}]}""",
            )

        // WHEN
        val result = provider.macroSeries(MacroSeriesId.TREASURY_YIELD_10Y, range)

        // THEN
        assertEquals("percent", result.data.unit)
        assertEquals(2, result.data.observations.size)
        assertEquals(
            BigDecimal("4.25"),
            result.data.observations
                .first()
                .value,
        )
        assertNull(
            result.data.observations
                .last()
                .value,
        )
        assertNull(result.data.currency)
    }

    @Test
    fun `GIVEN missing or cross instrument data WHEN normalizing metadata THEN a typed error is returned`() {
        // GIVEN
        val samples = listOf("{}" to MarketDataError.NOT_FOUND, """{"Symbol":"MSFT"}""" to MarketDataError.MALFORMED_RESPONSE)

        samples.forEach { (body, expected) ->
            val adapter =
                AlphaVantageProvider(
                    AlphaVantageTransport { parameters ->
                        mapper.readTree(if (parameters["function"] == "SYMBOL_SEARCH") """{"bestMatches":[]}""" else body)
                    },
                    clock,
                )

            // WHEN
            val result = runCatching { adapter.instrument(symbol) }

            // THEN
            assertEquals(expected, assertIs<MarketDataException>(result.exceptionOrNull()).code)
        }
    }

    @Test
    fun `GIVEN an ETF without a company overview WHEN fetching metadata THEN exact search metadata retains its own provenance`() {
        // GIVEN
        val calls = mutableListOf<String>()
        val adapter =
            AlphaVantageProvider(
                AlphaVantageTransport { parameters ->
                    calls += parameters.getValue("function")
                    mapper.readTree(
                        if (parameters["function"] == "OVERVIEW") {
                            "{}"
                        } else {
                            """{"bestMatches":[{"1. symbol":"SPY","2. name":"SPY","3. type":"ETF",
                        "4. region":"United States","8. currency":"USD"}]}"""
                        },
                    )
                },
                clock,
            )

        // WHEN
        val result = adapter.instrument(Symbol.parse("SPY"))

        // THEN
        assertEquals(listOf("OVERVIEW", "SYMBOL_SEARCH"), calls)
        assertEquals(AssetType.ETF, result.data.assetType)
        assertEquals("SYMBOL_SEARCH", result.provenance.dataset)
        assertNull(result.data.exchange)
    }

    @Test
    fun `GIVEN invalid or missing required fields WHEN normalizing reports THEN a safe typed error is returned`() {
        // GIVEN
        val samples =
            listOf(
                """{"symbol":"IBM","annualReports":[{"fiscalDateEnding":"2025-12-31",
                "reportedCurrency":"USD","totalRevenue":"secret-invalid"}]}""",
                """{"symbol":"IBM","annualReports":[{"fiscalDateEnding":"2025-12-31"}]}""",
                """{"symbol":"IBM","annualReports":[{"fiscalDateEnding":"2025-12-31",
                "reportedCurrency":"USD","totalRevenue":123456789012345.12345}]}""",
            )

        samples.forEach { body ->
            // WHEN
            val result = runCatching { provider(body).statements(symbol, StatementType.INCOME, ReportPeriod.ANNUAL) }

            // THEN
            val error = assertIs<MarketDataException>(result.exceptionOrNull())
            assertEquals(MarketDataError.MALFORMED_RESPONSE, error.code)
            assertTrue(!error.message.orEmpty().contains("secret-invalid"))
            assertNull(error.cause)
        }
    }
}
