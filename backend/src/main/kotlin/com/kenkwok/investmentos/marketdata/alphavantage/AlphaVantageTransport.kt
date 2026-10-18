package com.kenkwok.investmentos.marketdata.alphavantage

import com.kenkwok.investmentos.marketdata.MarketDataError
import com.kenkwok.investmentos.marketdata.MarketDataException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal fun interface AlphaVantageTransport {
    fun request(parameters: Map<String, String>): JsonNode
}

/** The request URL contains the API key: never log it or attach raw transport exceptions. */
internal class AlphaVantageHttpTransport(
    private val apiKey: String,
    private val client: HttpClient,
    private val timeout: Duration,
    private val clock: Clock,
    private val endpoint: URI = URI("https://www.alphavantage.co/query"),
    private val maxBytes: Int = 8 * 1024 * 1024,
) : AlphaVantageTransport {
    private val mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()

    init {
        require(timeout in Duration.ofMillis(100)..Duration.ofSeconds(30)) { "Provider timeout must be between 100ms and 30s" }
        require(maxBytes > 0) { "Provider response limit must be positive" }
        require(client.followRedirects() == HttpClient.Redirect.NEVER) { "Credential-bearing requests must not follow redirects" }
    }

    override fun request(parameters: Map<String, String>): JsonNode {
        if (apiKey.isBlank()) fail(MarketDataError.NOT_CONFIGURED)
        val query =
            (parameters + ("apikey" to apiKey)).entries.joinToString("&") { (key, value) ->
                "${URLEncoder.encode(key, StandardCharsets.UTF_8)}=${URLEncoder.encode(value, StandardCharsets.UTF_8)}"
            }
        val request =
            HttpRequest
                .newBuilder(URI("$endpoint?$query"))
                .timeout(timeout)
                .GET()
                .build()
        val pending = client.sendAsync(request) { LimitedBodySubscriber(maxBytes) }
        val response =
            try {
                pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                pending.cancel(true)
                fail(MarketDataError.TIMEOUT)
            } catch (_: InterruptedException) {
                pending.cancel(true)
                Thread.currentThread().interrupt()
                fail(MarketDataError.UNAVAILABLE)
            } catch (error: ExecutionException) {
                when (error.cause) {
                    is HttpTimeoutException -> fail(MarketDataError.TIMEOUT)
                    is ResponseTooLarge -> fail(MarketDataError.MALFORMED_RESPONSE)
                    else -> fail(MarketDataError.UNAVAILABLE)
                }
            }

        when (response.statusCode()) {
            200 -> Unit
            401, 403 -> fail(MarketDataError.AUTHENTICATION)
            404 -> fail(MarketDataError.NOT_FOUND)
            429 -> fail(MarketDataError.RATE_LIMITED, retryAfter(response.headers().firstValue("Retry-After").orElse(null)))
            in 400..499 -> fail(MarketDataError.UNSUPPORTED)
            else -> fail(MarketDataError.UNAVAILABLE)
        }
        val body =
            try {
                mapper.readTree(response.body())
            } catch (_: Exception) {
                fail(MarketDataError.MALFORMED_RESPONSE)
            }
        if (!body.isObject) fail(MarketDataError.MALFORMED_RESPONSE)
        val notice = listOf("Note", "Information", "Error Message").firstNotNullOfOrNull { key -> body.get(key)?.asString("") }
        if (notice != null) {
            val lower = notice.lowercase()
            when {
                listOf("rate limit", "call frequency", "requests per day", "calls per", "api frequency").any(lower::contains) ->
                    fail(MarketDataError.RATE_LIMITED)
                lower.contains("premium") || lower.contains("subscription") -> fail(MarketDataError.UNSUPPORTED)
                lower.contains("api key") || lower.contains("apikey") -> fail(MarketDataError.AUTHENTICATION)
                body.has("Error Message") -> fail(MarketDataError.UNSUPPORTED)
                else -> fail(MarketDataError.UNAVAILABLE)
            }
        }
        return body
    }

    private fun retryAfter(value: String?): Duration? {
        value ?: return null
        value.toLongOrNull()?.takeIf { it >= 0 }?.let { return Duration.ofSeconds(it) }
        return runCatching {
            Duration
                .between(clock.instant(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant())
                .takeUnless { it.isNegative }
        }.getOrNull()
    }

    private fun fail(
        code: MarketDataError,
        retryAfter: Duration? = null,
    ): Nothing = throw MarketDataException(code, "Market-data request failed: ${code.name}", "alpha-vantage", retryAfter)

    private class ResponseTooLarge : RuntimeException()

    private class LimitedBodySubscriber(
        private val limit: Int,
    ) : HttpResponse.BodySubscriber<ByteArray> {
        private val result = CompletableFuture<ByteArray>()
        private val bytes = ByteArrayOutputStream()
        private lateinit var subscription: Flow.Subscription

        override fun getBody(): CompletionStage<ByteArray> = result

        override fun onSubscribe(subscription: Flow.Subscription) {
            this.subscription = subscription
            subscription.request(1)
        }

        override fun onNext(buffers: List<ByteBuffer>) {
            if (buffers.sumOf { it.remaining().toLong() } + bytes.size() > limit) {
                subscription.cancel()
                result.completeExceptionally(ResponseTooLarge())
                return
            }
            buffers.forEach { buffer ->
                val chunk = ByteArray(buffer.remaining())
                buffer.get(chunk)
                bytes.write(chunk)
            }
            subscription.request(1)
        }

        override fun onError(throwable: Throwable) {
            result.completeExceptionally(throwable)
        }

        override fun onComplete() {
            result.complete(bytes.toByteArray())
        }
    }
}
