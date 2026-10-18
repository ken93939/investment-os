package com.kenkwok.investmentos.marketdata

import java.time.Duration

enum class MarketDataError {
    INVALID_INPUT,
    NOT_CONFIGURED,
    AUTHENTICATION,
    RATE_LIMITED,
    UNSUPPORTED,
    NOT_FOUND,
    TIMEOUT,
    UNAVAILABLE,
    MALFORMED_RESPONSE,
}

/** Messages and causes must never contain a credential-bearing URL or raw provider response. */
class MarketDataException(
    val code: MarketDataError,
    message: String,
    val provider: String? = null,
    val retryAfter: Duration? = null,
) : RuntimeException(message)
