package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.mapper

import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.DailyPricesResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.malformed
import com.kenkwok.investmentos.marketdata.domain.DateRange
import com.kenkwok.investmentos.marketdata.domain.PriceAdjustment
import com.kenkwok.investmentos.marketdata.domain.PriceBar
import com.kenkwok.investmentos.marketdata.domain.PriceHistory
import com.kenkwok.investmentos.marketdata.domain.Symbol
import java.time.LocalDate
import java.time.ZoneId

internal class PriceMapper {
    fun prices(
        response: DailyPricesResponse,
        symbol: Symbol,
        range: DateRange,
    ): PriceHistory =
        mapping {
            val metadata = response.metadata.requiredValue()
            if (Symbol.parse(metadata.symbol.required()) != symbol) malformed()
            val all =
                response.bars
                    .requiredValue()
                    .map { (date, bar) ->
                        PriceBar(
                            LocalDate.parse(date),
                            bar.open.decimal().requiredValue(),
                            bar.high.decimal().requiredValue(),
                            bar.low.decimal().requiredValue(),
                            bar.close.decimal().requiredValue(),
                            bar.volume.decimal()?.longValueExact(),
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
                metadata.timeZone.available()?.let(ZoneId::of),
                all.firstOrNull()?.let { DateRange(it.date, all.last().date) },
            )
        }

    fun sourceDate(response: DailyPricesResponse): LocalDate? =
        mapping {
            response.metadata
                ?.lastRefreshed
                .available()
                ?.let { LocalDate.parse(it.take(10)) }
        }
}
