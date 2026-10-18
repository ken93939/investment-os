package com.kenkwok.investmentos.marketdata.alphavantage

import com.kenkwok.investmentos.marketdata.AssetType
import com.kenkwok.investmentos.marketdata.DataFreshness
import com.kenkwok.investmentos.marketdata.DateRange
import com.kenkwok.investmentos.marketdata.FinancialDataProvider
import com.kenkwok.investmentos.marketdata.FinancialField
import com.kenkwok.investmentos.marketdata.FinancialReport
import com.kenkwok.investmentos.marketdata.FinancialStatements
import com.kenkwok.investmentos.marketdata.Instrument
import com.kenkwok.investmentos.marketdata.MacroObservation
import com.kenkwok.investmentos.marketdata.MacroSeries
import com.kenkwok.investmentos.marketdata.MacroSeriesId
import com.kenkwok.investmentos.marketdata.MarketDataError
import com.kenkwok.investmentos.marketdata.MarketDataException
import com.kenkwok.investmentos.marketdata.PriceAdjustment
import com.kenkwok.investmentos.marketdata.PriceBar
import com.kenkwok.investmentos.marketdata.PriceHistory
import com.kenkwok.investmentos.marketdata.Provenance
import com.kenkwok.investmentos.marketdata.ReportPeriod
import com.kenkwok.investmentos.marketdata.Sourced
import com.kenkwok.investmentos.marketdata.StatementType
import com.kenkwok.investmentos.marketdata.Symbol
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency

internal class AlphaVantageProvider(
    private val transport: AlphaVantageTransport,
    private val clock: Clock,
) : FinancialDataProvider {
    override val id = "alpha-vantage"

    override fun search(query: String): Sourced<List<Instrument>> =
        load("SYMBOL_SEARCH", mapOf("keywords" to query), DataFreshness.UNKNOWN) { body ->
            array(body, "bestMatches").mapNotNull { row ->
                val type = assetType(required(row, "3. type"))
                if (required(row, "4. region") != "United States" || type == AssetType.UNKNOWN) {
                    null
                } else {
                    Instrument(
                        Symbol.parse(required(row, "1. symbol")),
                        required(row, "2. name"),
                        type,
                        optional(row, "8. currency")?.let(Currency::getInstance),
                        null,
                        optional(row, "7. timezone")?.let(ZoneId::of),
                        "US",
                    )
                }
            }
        }

    override fun instrument(symbol: Symbol): Sourced<Instrument> =
        try {
            overview(symbol)
        } catch (error: MarketDataException) {
            if (error.code != MarketDataError.NOT_FOUND) throw error
            // OVERVIEW can be empty for ETFs. Search supplies limited, explicitly incomplete metadata.
            val matches = search(symbol.value)
            val etf = matches.data.singleOrNull { it.symbol == symbol && it.assetType == AssetType.ETF } ?: throw error
            Sourced(etf, matches.provenance)
        }

    private fun overview(symbol: Symbol): Sourced<Instrument> =
        load("OVERVIEW", mapOf("symbol" to symbol.value), DataFreshness.UNKNOWN) { body ->
            if (body.isEmpty) fail(MarketDataError.NOT_FOUND)
            verifySymbol(body, "Symbol", symbol)
            val country = optional(body, "Country")
            if (country != null && country !in setOf("USA", "US", "United States")) fail(MarketDataError.UNSUPPORTED)
            val exchange = optional(body, "Exchange")
            val type = assetType(required(body, "AssetType"))
            if (type == AssetType.UNKNOWN) fail(MarketDataError.UNSUPPORTED)
            Instrument(
                symbol,
                required(body, "Name"),
                type,
                optional(body, "Currency")?.let(Currency::getInstance),
                exchange,
                if (exchange in setOf("NYSE", "NASDAQ", "AMEX", "NYSE ARCA")) ZoneId.of("America/New_York") else null,
                country?.let { "US" },
            )
        }

    override fun prices(
        symbol: Symbol,
        range: DateRange,
        adjustment: PriceAdjustment,
    ): Sourced<PriceHistory> {
        if (adjustment != PriceAdjustment.RAW) fail(MarketDataError.UNSUPPORTED)
        return load("TIME_SERIES_DAILY", mapOf("symbol" to symbol.value, "outputsize" to "compact"), DataFreshness.HISTORICAL) { body ->
            val metadata = body.get("Meta Data") ?: malformed()
            verifySymbol(metadata, "2. Symbol", symbol)
            val timeZone = optional(metadata, "5. Time Zone")?.let(ZoneId::of)
            val series = body.get("Time Series (Daily)") ?: malformed()
            if (!series.isObject) malformed()
            val all =
                series
                    .properties()
                    .map { (date, bar) ->
                        PriceBar(
                            LocalDate.parse(date),
                            decimal(bar, "1. open") ?: malformed(),
                            decimal(bar, "2. high") ?: malformed(),
                            decimal(bar, "3. low") ?: malformed(),
                            decimal(bar, "4. close") ?: malformed(),
                            decimal(bar, "5. volume")?.longValueExact(),
                        ).also { value ->
                            if (value.low.signum() < 0 ||
                                value.high < maxOf(value.open, value.close, value.low) ||
                                value.low > minOf(value.open, value.close) ||
                                (value.volume != null && value.volume < 0)
                            ) {
                                malformed()
                            }
                        }
                    }.sortedBy { it.date }
            PriceHistory(
                symbol,
                all.filter { it.date in range.from..range.to },
                PriceAdjustment.RAW,
                null,
                null,
                timeZone,
                all.firstOrNull()?.let { DateRange(it.date, all.last().date) },
            )
        }
    }

    override fun statements(
        symbol: Symbol,
        type: StatementType,
        period: ReportPeriod,
    ): Sourced<FinancialStatements> {
        val (function, fields) =
            when (type) {
                StatementType.INCOME ->
                    "INCOME_STATEMENT" to
                        mapOf(
                            FinancialField.REVENUE to "totalRevenue",
                            FinancialField.COST_OF_REVENUE to "costOfRevenue",
                            FinancialField.GROSS_PROFIT to "grossProfit",
                            FinancialField.OPERATING_INCOME to "operatingIncome",
                            FinancialField.NET_INCOME to "netIncome",
                        )
                StatementType.BALANCE_SHEET ->
                    "BALANCE_SHEET" to
                        mapOf(
                            FinancialField.TOTAL_ASSETS to "totalAssets",
                            FinancialField.TOTAL_LIABILITIES to "totalLiabilities",
                            FinancialField.SHAREHOLDER_EQUITY to "totalShareholderEquity",
                            FinancialField.CASH_AND_EQUIVALENTS to "cashAndCashEquivalentsAtCarryingValue",
                        )
                StatementType.CASH_FLOW ->
                    "CASH_FLOW" to
                        mapOf(
                            FinancialField.OPERATING_CASH_FLOW to "operatingCashflow",
                            FinancialField.CAPITAL_EXPENDITURE to "capitalExpenditures",
                            FinancialField.DIVIDENDS_PAID to "dividendPayout",
                        )
            }
        return load(function, mapOf("symbol" to symbol.value), DataFreshness.HISTORICAL) { body ->
            if (body.isEmpty) fail(MarketDataError.NOT_FOUND)
            verifySymbol(body, "symbol", symbol)
            val reports =
                array(body, if (period == ReportPeriod.ANNUAL) "annualReports" else "quarterlyReports")
                    .map { row ->
                        FinancialReport(
                            LocalDate.parse(required(row, "fiscalDateEnding")),
                            period,
                            Currency.getInstance(required(row, "reportedCurrency")),
                            fields.mapValues { (_, key) -> decimal(row, key) },
                        )
                    }.sortedBy { it.fiscalPeriodEnd }
            if (reports.map { it.fiscalPeriodEnd }.distinct().size != reports.size) malformed()
            FinancialStatements(symbol, type, reports)
        }
    }

    override fun macroSeries(
        series: MacroSeriesId,
        range: DateRange,
    ): Sourced<MacroSeries> {
        val function = if (series == MacroSeriesId.TREASURY_YIELD_10Y) "TREASURY_YIELD" else series.name
        val parameters =
            if (series ==
                MacroSeriesId.TREASURY_YIELD_10Y
            ) {
                mapOf("maturity" to "10year", "interval" to "monthly")
            } else {
                emptyMap()
            }
        return load(function, parameters, DataFreshness.HISTORICAL) { body ->
            val observations =
                array(body, "data")
                    .map { row ->
                        MacroObservation(LocalDate.parse(required(row, "date")), decimal(row, "value"))
                    }.filter { it.date in range.from..range.to }
                    .sortedBy { it.date }
            if (observations.map { it.date }.distinct().size != observations.size) malformed()
            MacroSeries(
                series,
                required(body, "name"),
                required(body, "interval"),
                required(body, "unit"),
                if (series == MacroSeriesId.REAL_GDP) Currency.getInstance("USD") else null,
                observations,
            )
        }
    }

    private fun <T> load(
        function: String,
        parameters: Map<String, String>,
        freshness: DataFreshness,
        normalize: (JsonNode) -> T,
    ): Sourced<T> {
        val body = transport.request(parameters + ("function" to function))
        return try {
            Sourced(
                normalize(body),
                Provenance(
                    id,
                    function,
                    clock.instant(),
                    freshness,
                    sourceDate = optional(body.path("Meta Data"), "3. Last Refreshed")?.let { LocalDate.parse(it.take(10)) },
                ),
            )
        } catch (error: MarketDataException) {
            if (error.code == MarketDataError.INVALID_INPUT) malformed() else throw error
        } catch (_: Exception) {
            malformed()
        }
    }

    private fun required(
        node: JsonNode,
        field: String,
    ): String = optional(node, field) ?: malformed()

    private fun optional(
        node: JsonNode,
        field: String,
    ): String? {
        val value = node.get(field) ?: return null
        if (value.isNull) return null
        // Alpha Vantage documents these fields as strings; do not round unexpected JSON floats via Double.
        if (!value.isString) malformed()
        return value.asString().takeUnless { it in setOf("", "None", "null", "-", ".") }
    }

    private fun decimal(
        node: JsonNode,
        field: String,
    ): BigDecimal? = optional(node, field)?.toBigDecimal()

    private fun array(
        node: JsonNode,
        field: String,
    ): List<JsonNode> {
        val value = node.get(field) ?: malformed()
        if (!value.isArray) malformed()
        return value.toList()
    }

    private fun verifySymbol(
        node: JsonNode,
        field: String,
        expected: Symbol,
    ) {
        if (Symbol.parse(required(node, field)) != expected) malformed()
    }

    private fun assetType(value: String): AssetType =
        when (value) {
            "Common Stock", "Equity" -> AssetType.STOCK
            "ETF" -> AssetType.ETF
            else -> AssetType.UNKNOWN
        }

    private fun malformed(): Nothing = fail(MarketDataError.MALFORMED_RESPONSE)

    private fun fail(code: MarketDataError): Nothing = throw MarketDataException(code, "Market-data normalization failed: ${code.name}", id)
}
