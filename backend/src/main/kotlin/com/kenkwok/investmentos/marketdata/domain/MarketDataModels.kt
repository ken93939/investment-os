package com.kenkwok.investmentos.marketdata.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Currency
import java.util.Locale

@JvmInline
value class Symbol private constructor(
    val value: String,
) {
    companion object {
        fun parse(input: String): Symbol {
            val normalized = input.trim().uppercase(Locale.ROOT)
            if (!normalized.matches(Regex("[A-Z][A-Z0-9]*([.-][A-Z0-9]+)*")) || normalized.length > 20) {
                throw InvalidMarketDataValue("Use a valid stock or ETF symbol of at most 20 characters")
            }
            return Symbol(normalized)
        }
    }
}

data class DateRange(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        if (from > to) throw InvalidMarketDataValue("Start date must not follow end date")
    }
}

enum class AssetType { STOCK, ETF, UNKNOWN }

enum class DataFreshness { LIVE, DELAYED, HISTORICAL, UNKNOWN }

enum class PriceAdjustment { RAW, SPLIT_AND_DIVIDEND_ADJUSTED }

enum class StatementType { INCOME, BALANCE_SHEET, CASH_FLOW }

enum class ReportPeriod { ANNUAL, QUARTERLY }

enum class FinancialField {
    REVENUE,
    COST_OF_REVENUE,
    GROSS_PROFIT,
    OPERATING_INCOME,
    NET_INCOME,
    TOTAL_ASSETS,
    TOTAL_LIABILITIES,
    SHAREHOLDER_EQUITY,
    CASH_AND_EQUIVALENTS,
    OPERATING_CASH_FLOW,
    CAPITAL_EXPENDITURE,
    DIVIDENDS_PAID,
}

enum class MacroSeriesId { REAL_GDP, CPI, UNEMPLOYMENT, FEDERAL_FUNDS_RATE, TREASURY_YIELD_10Y }

/** Retrieval time is not a quote's as-of time or a financial report's publication time. */
data class Provenance(
    val provider: String,
    val dataset: String,
    val retrievedAt: Instant,
    val freshness: DataFreshness,
    val sourceAsOf: Instant? = null,
    val sourceDate: LocalDate? = null,
)

data class Sourced<T>(
    val data: T,
    val provenance: Provenance,
)

data class Instrument(
    val symbol: Symbol,
    val name: String,
    val assetType: AssetType,
    val currency: Currency?,
    val exchange: String?,
    val timeZone: ZoneId?,
    val country: String?,
)

data class PriceBar(
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long?,
)

data class PriceHistory(
    val symbol: Symbol,
    val bars: List<PriceBar>,
    val adjustment: PriceAdjustment,
    val currency: Currency?,
    val exchange: String?,
    val timeZone: ZoneId?,
    // The available response window is not a guarantee of complete trading-day coverage.
    val availableRange: DateRange?,
)

data class FinancialReport(
    val fiscalPeriodEnd: LocalDate,
    val period: ReportPeriod,
    val currency: Currency,
    val values: Map<FinancialField, BigDecimal?>,
    val publishedAt: Instant? = null,
)

data class FinancialStatements(
    val symbol: Symbol,
    val type: StatementType,
    val reports: List<FinancialReport>,
)

data class MacroObservation(
    val date: LocalDate,
    val value: BigDecimal?,
)

data class MacroSeries(
    val id: MacroSeriesId,
    val name: String,
    val interval: String,
    val unit: String,
    val currency: Currency?,
    val observations: List<MacroObservation>,
)
