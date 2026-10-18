package com.kenkwok.investmentos.marketdata.application.port.out

import com.kenkwok.investmentos.marketdata.domain.DateRange
import com.kenkwok.investmentos.marketdata.domain.FinancialStatements
import com.kenkwok.investmentos.marketdata.domain.Instrument
import com.kenkwok.investmentos.marketdata.domain.MacroSeries
import com.kenkwok.investmentos.marketdata.domain.MacroSeriesId
import com.kenkwok.investmentos.marketdata.domain.PriceAdjustment
import com.kenkwok.investmentos.marketdata.domain.PriceHistory
import com.kenkwok.investmentos.marketdata.domain.ReportPeriod
import com.kenkwok.investmentos.marketdata.domain.Sourced
import com.kenkwok.investmentos.marketdata.domain.StatementType
import com.kenkwok.investmentos.marketdata.domain.Symbol

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
