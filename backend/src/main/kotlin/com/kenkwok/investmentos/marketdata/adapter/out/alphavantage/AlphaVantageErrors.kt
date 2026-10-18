package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage

import com.kenkwok.investmentos.marketdata.application.MarketDataError
import com.kenkwok.investmentos.marketdata.application.MarketDataException
import java.time.Duration

internal fun providerFailure(
    code: MarketDataError,
    retryAfter: Duration? = null,
): Nothing = throw MarketDataException(code, "Market-data request failed: ${code.name}", "alpha-vantage", retryAfter)

internal fun malformed(): Nothing = providerFailure(MarketDataError.MALFORMED_RESPONSE)
