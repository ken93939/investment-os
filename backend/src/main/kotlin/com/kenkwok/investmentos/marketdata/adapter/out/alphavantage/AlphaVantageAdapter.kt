package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage

import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.DailyPricesRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.MacroFunction
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.MacroInterval
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.MacroSeriesRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.OverviewRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.StatementRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.SymbolSearchRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.TreasuryMaturity
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.mapper.InstrumentMapper
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.mapper.MacroMapper
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.mapper.PriceMapper
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.mapper.StatementMapper
import com.kenkwok.investmentos.marketdata.application.MarketDataError
import com.kenkwok.investmentos.marketdata.application.MarketDataException
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
import java.time.Clock
import java.time.LocalDate

internal class AlphaVantageAdapter(
    private val client: AlphaVantageRestClient,
    private val clock: Clock,
) : FinancialDataProvider {
    override val id = "alpha-vantage"
    private val instruments = InstrumentMapper()
    private val prices = PriceMapper()
    private val statements = StatementMapper()
    private val macro = MacroMapper()

    override fun search(query: String): Sourced<List<Instrument>> =
        sourced(instruments.search(client.search(SymbolSearchRequest(query))), "SYMBOL_SEARCH", DataFreshness.UNKNOWN)

    override fun instrument(symbol: Symbol): Sourced<Instrument> =
        try {
            sourced(instruments.overview(client.overview(OverviewRequest(symbol.value)), symbol), "OVERVIEW", DataFreshness.UNKNOWN)
        } catch (error: MarketDataException) {
            if (error.code != MarketDataError.NOT_FOUND) throw error
            val matches = search(symbol.value)
            val etf = matches.data.singleOrNull { it.symbol == symbol && it.assetType == AssetType.ETF } ?: throw error
            Sourced(etf, matches.provenance)
        }

    override fun prices(
        symbol: Symbol,
        range: DateRange,
        adjustment: PriceAdjustment,
    ): Sourced<PriceHistory> {
        if (adjustment != PriceAdjustment.RAW) providerFailure(MarketDataError.UNSUPPORTED)
        val response = client.dailyPrices(DailyPricesRequest(symbol.value))
        return sourced(prices.prices(response, symbol, range), "TIME_SERIES_DAILY", DataFreshness.HISTORICAL, prices.sourceDate(response))
    }

    override fun statements(
        symbol: Symbol,
        type: StatementType,
        period: ReportPeriod,
    ): Sourced<FinancialStatements> {
        val request = StatementRequest(symbol.value)
        val (result, dataset) =
            when (type) {
                StatementType.INCOME -> statements.income(client.incomeStatement(request), symbol, period) to "INCOME_STATEMENT"
                StatementType.BALANCE_SHEET -> statements.balanceSheet(client.balanceSheet(request), symbol, period) to "BALANCE_SHEET"
                StatementType.CASH_FLOW -> statements.cashFlow(client.cashFlow(request), symbol, period) to "CASH_FLOW"
            }
        return sourced(result, dataset, DataFreshness.HISTORICAL)
    }

    override fun macroSeries(
        series: MacroSeriesId,
        range: DateRange,
    ): Sourced<MacroSeries> {
        val request =
            when (series) {
                MacroSeriesId.TREASURY_YIELD_10Y ->
                    MacroSeriesRequest(
                        MacroFunction.TREASURY_YIELD,
                        MacroInterval.MONTHLY,
                        TreasuryMaturity.TEN_YEAR,
                    )
                MacroSeriesId.REAL_GDP -> MacroSeriesRequest(MacroFunction.REAL_GDP)
                MacroSeriesId.CPI -> MacroSeriesRequest(MacroFunction.CPI)
                MacroSeriesId.UNEMPLOYMENT -> MacroSeriesRequest(MacroFunction.UNEMPLOYMENT)
                MacroSeriesId.FEDERAL_FUNDS_RATE -> MacroSeriesRequest(MacroFunction.FEDERAL_FUNDS_RATE)
            }
        return sourced(macro.series(client.macroSeries(request), series, range), request.function.name, DataFreshness.HISTORICAL)
    }

    private fun <T> sourced(
        data: T,
        dataset: String,
        freshness: DataFreshness,
        sourceDate: LocalDate? = null,
    ): Sourced<T> = Sourced(data, Provenance(id, dataset, clock.instant(), freshness, sourceDate = sourceDate))
}
