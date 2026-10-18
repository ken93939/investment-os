package com.kenkwok.investmentos.marketdata

import java.time.LocalDate

/** Callers supply application inputs and receive application models, regardless of provider. */
class MarketDataGateway(
    primary: String,
    providers: List<FinancialDataProvider>,
) {
    private val provider: FinancialDataProvider?

    init {
        require(providers.map { it.id }.distinct().size == providers.size) { "Provider IDs must be unique" }
        provider = providers.singleOrNull { it.id == primary }
        require(primary == "none" || provider != null) { "Unknown primary market-data provider" }
    }

    fun search(query: String): Sourced<List<Instrument>> {
        val normalized = query.trim()
        if (normalized.length !in 1..100 || normalized.any { it.isISOControl() }) {
            throw MarketDataException(MarketDataError.INVALID_INPUT, "Search text must contain between 1 and 100 printable characters")
        }
        return selected().search(normalized)
    }

    fun instrument(symbol: String): Sourced<Instrument> {
        val parsed = Symbol.parse(symbol)
        return selected().instrument(parsed)
    }

    fun prices(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
        adjustment: PriceAdjustment = PriceAdjustment.RAW,
    ): Sourced<PriceHistory> {
        val parsed = Symbol.parse(symbol)
        val range = DateRange(from, to)
        return selected().prices(parsed, range, adjustment)
    }

    fun statements(
        symbol: String,
        type: StatementType,
        period: ReportPeriod,
    ): Sourced<FinancialStatements> {
        val parsed = Symbol.parse(symbol)
        return selected().statements(parsed, type, period)
    }

    fun macroSeries(
        series: MacroSeriesId,
        from: LocalDate,
        to: LocalDate,
    ): Sourced<MacroSeries> {
        val range = DateRange(from, to)
        return selected().macroSeries(series, range)
    }

    private fun selected(): FinancialDataProvider =
        provider ?: throw MarketDataException(MarketDataError.NOT_CONFIGURED, "A market-data provider has not been configured")
}
