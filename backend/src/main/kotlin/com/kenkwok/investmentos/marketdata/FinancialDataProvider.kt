package com.kenkwok.investmentos.marketdata

interface InstrumentProvider {
    fun search(query: String): Sourced<List<Instrument>>

    fun instrument(symbol: Symbol): Sourced<Instrument>
}

interface HistoricalPriceProvider {
    fun prices(
        symbol: Symbol,
        range: DateRange,
        adjustment: PriceAdjustment = PriceAdjustment.RAW,
    ): Sourced<PriceHistory>
}

interface FinancialStatementProvider {
    fun statements(
        symbol: Symbol,
        type: StatementType,
        period: ReportPeriod,
    ): Sourced<FinancialStatements>
}

interface MacroSeriesProvider {
    fun macroSeries(
        series: MacroSeriesId,
        range: DateRange,
    ): Sourced<MacroSeries>
}

interface FinancialDataProvider :
    InstrumentProvider,
    HistoricalPriceProvider,
    FinancialStatementProvider,
    MacroSeriesProvider {
    val id: String
}
