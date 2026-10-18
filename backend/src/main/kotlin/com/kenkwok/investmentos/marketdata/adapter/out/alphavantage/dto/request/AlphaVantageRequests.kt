package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request

internal data class SymbolSearchRequest(
    val keywords: String,
)

internal data class OverviewRequest(
    val symbol: String,
)

internal enum class OutputSize(
    val parameter: String,
) {
    COMPACT("compact"),
}

internal data class DailyPricesRequest(
    val symbol: String,
    val outputSize: OutputSize = OutputSize.COMPACT,
)

internal data class StatementRequest(
    val symbol: String,
)

internal enum class MacroFunction { REAL_GDP, CPI, UNEMPLOYMENT, FEDERAL_FUNDS_RATE, TREASURY_YIELD }

internal enum class MacroInterval(
    val parameter: String,
) {
    MONTHLY("monthly"),
}

internal enum class TreasuryMaturity(
    val parameter: String,
) {
    TEN_YEAR("10year"),
}

internal data class MacroSeriesRequest(
    val function: MacroFunction,
    val interval: MacroInterval? = null,
    val maturity: TreasuryMaturity? = null,
)
