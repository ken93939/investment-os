package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.mapper

import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.MacroSeriesResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.malformed
import com.kenkwok.investmentos.marketdata.domain.DateRange
import com.kenkwok.investmentos.marketdata.domain.MacroObservation
import com.kenkwok.investmentos.marketdata.domain.MacroSeries
import com.kenkwok.investmentos.marketdata.domain.MacroSeriesId
import java.time.LocalDate
import java.util.Currency

internal class MacroMapper {
    fun series(
        response: MacroSeriesResponse,
        series: MacroSeriesId,
        range: DateRange,
    ): MacroSeries =
        mapping {
            val observations =
                response.data
                    .requiredValue()
                    .map { row ->
                        MacroObservation(LocalDate.parse(row.date.required()), row.value.decimal())
                    }.filter { it.date in range.from..range.to }
                    .sortedBy { it.date }
            if (observations.map { it.date }.distinct().size != observations.size) malformed()
            MacroSeries(
                series,
                response.name.required(),
                response.interval.required(),
                response.unit.required(),
                if (series == MacroSeriesId.REAL_GDP) Currency.getInstance("USD") else null,
                observations,
            )
        }
}
