package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage

import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.DailyPricesRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.MacroSeriesRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.OverviewRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.StatementRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.SymbolSearchRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.BalanceSheetResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.CashFlowResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.DailyPricesResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.IncomeStatementResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.MacroSeriesResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.OverviewResponse
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.response.SymbolSearchResponse
import com.kenkwok.investmentos.marketdata.application.MarketDataError
import com.kenkwok.investmentos.marketdata.application.MarketDataException
import org.springframework.web.client.RestClient
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.cfg.CoercionAction
import tools.jackson.databind.cfg.CoercionInputShape
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.type.LogicalType
import tools.jackson.module.kotlin.KotlinModule
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Only this class knows provider envelope JSON. Callers receive endpoint-specific DTOs. */
internal class AlphaVantageRestClient(
    private val restClient: RestClient,
    private val clock: Clock,
    private val endpoint: URI = URI("https://www.alphavantage.co/query"),
) {
    private val mapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .withCoercionConfig(LogicalType.Textual) { config ->
                listOf(CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean).forEach {
                    config.setCoercion(it, CoercionAction.Fail)
                }
            }.build()

    fun search(request: SymbolSearchRequest): SymbolSearchResponse =
        load("SYMBOL_SEARCH", mapOf("keywords" to request.keywords), SymbolSearchResponse::class.java)

    fun overview(request: OverviewRequest): OverviewResponse =
        load("OVERVIEW", mapOf("symbol" to request.symbol), OverviewResponse::class.java, emptyIsNotFound = true)

    fun dailyPrices(request: DailyPricesRequest): DailyPricesResponse =
        load(
            "TIME_SERIES_DAILY",
            mapOf("symbol" to request.symbol, "outputsize" to request.outputSize.parameter),
            DailyPricesResponse::class.java,
        )

    fun incomeStatement(request: StatementRequest): IncomeStatementResponse =
        load("INCOME_STATEMENT", mapOf("symbol" to request.symbol), IncomeStatementResponse::class.java, emptyIsNotFound = true)

    fun balanceSheet(request: StatementRequest): BalanceSheetResponse =
        load("BALANCE_SHEET", mapOf("symbol" to request.symbol), BalanceSheetResponse::class.java, emptyIsNotFound = true)

    fun cashFlow(request: StatementRequest): CashFlowResponse =
        load("CASH_FLOW", mapOf("symbol" to request.symbol), CashFlowResponse::class.java, emptyIsNotFound = true)

    fun macroSeries(request: MacroSeriesRequest): MacroSeriesResponse =
        load(
            request.function.name,
            buildMap {
                request.interval?.let { put("interval", it.parameter) }
                request.maturity?.let { put("maturity", it.parameter) }
            },
            MacroSeriesResponse::class.java,
        )

    private fun <T> load(
        function: String,
        parameters: Map<String, String>,
        type: Class<T>,
        emptyIsNotFound: Boolean = false,
    ): T {
        val query =
            (parameters + ("function" to function)).entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
            }
        return try {
            restClient.get().uri(URI("$endpoint?$query")).exchange { _, response ->
                when (response.statusCode.value()) {
                    200 -> Unit
                    401, 403 -> providerFailure(MarketDataError.AUTHENTICATION)
                    404 -> providerFailure(MarketDataError.NOT_FOUND)
                    429 -> providerFailure(MarketDataError.RATE_LIMITED, retryAfter(response.headers.getFirst("Retry-After")))
                    in 400..499 -> providerFailure(MarketDataError.UNSUPPORTED)
                    else -> providerFailure(MarketDataError.UNAVAILABLE)
                }
                val body =
                    try {
                        mapper.readTree(response.body)
                    } catch (_: Exception) {
                        malformed()
                    }
                if (body == null || !body.isObject) malformed()
                checkEnvelope(body)
                if (emptyIsNotFound && body.isEmpty) providerFailure(MarketDataError.NOT_FOUND)
                try {
                    mapper.treeToValue(body, type)
                } catch (_: Exception) {
                    malformed()
                }
            } ?: malformed()
        } catch (error: MarketDataException) {
            throw error
        } catch (_: Exception) {
            // RestClient exceptions can contain URLs and response bodies: never retain their causes.
            providerFailure(MarketDataError.UNAVAILABLE)
        }
    }

    private fun checkEnvelope(body: JsonNode) {
        val notice = listOf("Note", "Information", "Error Message").firstNotNullOfOrNull { body.get(it)?.asString("") } ?: return
        val lower = notice.lowercase()
        when {
            listOf(
                "rate limit",
                "call frequency",
                "requests per day",
                "calls per",
                "api frequency",
            ).any(lower::contains) -> providerFailure(MarketDataError.RATE_LIMITED)
            lower.contains("premium") || lower.contains("subscription") -> providerFailure(MarketDataError.UNSUPPORTED)
            lower.contains("api key") || lower.contains("apikey") -> providerFailure(MarketDataError.AUTHENTICATION)
            body.has("Error Message") -> providerFailure(MarketDataError.UNSUPPORTED)
            else -> providerFailure(MarketDataError.UNAVAILABLE)
        }
    }

    private fun retryAfter(value: String?): Duration? {
        value ?: return null
        value.toLongOrNull()?.takeIf { it >= 0 }?.let { return Duration.ofSeconds(it) }
        return runCatching {
            Duration
                .between(
                    clock.instant(),
                    ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(),
                ).takeUnless { it.isNegative }
        }.getOrNull()
    }
}
