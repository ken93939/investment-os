package com.kenkwok.investmentos.marketdata.application

import com.kenkwok.investmentos.marketdata.application.port.out.FinancialDataProvider
import com.kenkwok.investmentos.marketdata.domain.DateRange
import com.kenkwok.investmentos.marketdata.domain.FinancialStatements
import com.kenkwok.investmentos.marketdata.domain.Instrument
import com.kenkwok.investmentos.marketdata.domain.InvalidMarketDataValue
import com.kenkwok.investmentos.marketdata.domain.MacroSeries
import com.kenkwok.investmentos.marketdata.domain.MacroSeriesId
import com.kenkwok.investmentos.marketdata.domain.PriceAdjustment
import com.kenkwok.investmentos.marketdata.domain.PriceHistory
import com.kenkwok.investmentos.marketdata.domain.ReportPeriod
import com.kenkwok.investmentos.marketdata.domain.Sourced
import com.kenkwok.investmentos.marketdata.domain.StatementType
import com.kenkwok.investmentos.marketdata.domain.Symbol
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
        val parsed = validate { Symbol.parse(symbol) }
        return selected().instrument(parsed)
    }

    fun prices(
        symbol: String,
        from: LocalDate,
        to: LocalDate,
        adjustment: PriceAdjustment = PriceAdjustment.RAW,
    ): Sourced<PriceHistory> {
        val parsed = validate { Symbol.parse(symbol) }
        val range = validate { DateRange(from, to) }
        return selected().prices(parsed, range, adjustment)
    }

    fun statements(
        symbol: String,
        type: StatementType,
        period: ReportPeriod,
    ): Sourced<FinancialStatements> {
        val parsed = validate { Symbol.parse(symbol) }
        return selected().statements(parsed, type, period)
    }

    fun macroSeries(
        series: MacroSeriesId,
        from: LocalDate,
        to: LocalDate,
    ): Sourced<MacroSeries> {
        val range = validate { DateRange(from, to) }
        return selected().macroSeries(series, range)
    }

    private inline fun <T> validate(action: () -> T): T =
        try {
            action()
        } catch (error: InvalidMarketDataValue) {
            throw MarketDataException(MarketDataError.INVALID_INPUT, error.message ?: "Invalid market-data input")
        }

    private fun selected(): FinancialDataProvider =
        provider ?: throw MarketDataException(MarketDataError.NOT_CONFIGURED, "A market-data provider has not been configured")
}
