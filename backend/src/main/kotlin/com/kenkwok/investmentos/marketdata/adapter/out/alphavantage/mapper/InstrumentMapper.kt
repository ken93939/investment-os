package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.mapper

import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.OverviewResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.SymbolSearchResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.malformed
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.providerFailure
import com.kenkwok.investmentos.marketdata.application.MarketDataError
import com.kenkwok.investmentos.marketdata.domain.AssetType
import com.kenkwok.investmentos.marketdata.domain.Instrument
import com.kenkwok.investmentos.marketdata.domain.Symbol
import java.time.ZoneId
import java.util.Currency

internal class InstrumentMapper {
    fun search(response: SymbolSearchResponse): List<Instrument> =
        mapping {
            response.matches.requiredValue().mapNotNull { row ->
                val type = assetType(row.type.required())
                if (row.region.required() != "United States" || type == AssetType.UNKNOWN) {
                    null
                } else {
                    Instrument(
                        Symbol.parse(row.symbol.required()),
                        row.name.required(),
                        type,
                        row.currency.available()?.let(Currency::getInstance),
                        null,
                        row.timeZone.available()?.let(ZoneId::of),
                        "US",
                    )
                }
            }
        }

    fun overview(
        response: OverviewResponse,
        symbol: Symbol,
    ): Instrument =
        mapping {
            if (Symbol.parse(response.symbol.required()) != symbol) malformed()
            val country = response.country.available()
            if (country != null && country !in setOf("USA", "US", "United States")) providerFailure(MarketDataError.UNSUPPORTED)
            val type = assetType(response.assetType.required())
            if (type == AssetType.UNKNOWN) providerFailure(MarketDataError.UNSUPPORTED)
            val exchange = response.exchange.available()
            Instrument(
                symbol,
                response.name.required(),
                type,
                response.currency.available()?.let(Currency::getInstance),
                exchange,
                if (exchange in setOf("NYSE", "NASDAQ", "AMEX", "NYSE ARCA")) ZoneId.of("America/New_York") else null,
                country?.let { "US" },
            )
        }

    private fun assetType(value: String): AssetType =
        when (value) {
            "Common Stock", "Equity" -> AssetType.STOCK
            "ETF" -> AssetType.ETF
            else -> AssetType.UNKNOWN
        }
}
