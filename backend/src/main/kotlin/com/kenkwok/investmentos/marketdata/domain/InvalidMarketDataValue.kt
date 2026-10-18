package com.kenkwok.investmentos.marketdata.domain

/** Domain validation never includes the untrusted input in its message. */
class InvalidMarketDataValue(
    message: String,
) : IllegalArgumentException(message)
