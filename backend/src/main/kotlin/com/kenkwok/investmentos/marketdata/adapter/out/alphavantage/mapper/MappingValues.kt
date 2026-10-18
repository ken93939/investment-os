package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.mapper

import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.malformed
import com.kenkwok.investmentos.marketdata.domain.InvalidMarketDataValue
import java.math.BigDecimal
import java.time.DateTimeException

internal fun String?.available(): String? = this?.takeUnless { it in setOf("", "None", "null", "-", ".") }

internal fun String?.required(): String = available() ?: malformed()

internal fun String?.decimal(): BigDecimal? = available()?.toBigDecimal()

internal fun <T : Any> T?.requiredValue(): T = this ?: malformed()

/** Sanitize known data-conversion failures without hiding unrelated programming failures. */
internal inline fun <T> mapping(action: () -> T): T =
    try {
        action()
    } catch (_: InvalidMarketDataValue) {
        malformed()
    } catch (_: DateTimeException) {
        malformed()
    } catch (_: IllegalArgumentException) {
        malformed()
    } catch (_: ArithmeticException) {
        malformed()
    }
