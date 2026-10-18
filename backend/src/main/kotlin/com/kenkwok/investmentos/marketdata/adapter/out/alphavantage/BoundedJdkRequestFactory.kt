package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage

import com.kenkwok.investmentos.marketdata.application.MarketDataError
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatusCode
import org.springframework.http.client.ClientHttpRequest
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.http.client.ClientHttpResponse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException
import java.util.concurrent.Flow
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Buffers at most maxBytes, within one deadline; the injected JDK client is owned by Spring. */
internal class BoundedJdkRequestFactory(
    private val apiKey: String,
    private val client: HttpClient,
    private val timeout: Duration,
    private val maxBytes: Int = 8 * 1024 * 1024,
) : ClientHttpRequestFactory {
    init {
        require(timeout in Duration.ofMillis(100)..Duration.ofSeconds(30)) { "Provider timeout must be between 100ms and 30s" }
        require(maxBytes > 0) { "Provider response limit must be positive" }
        require(client.followRedirects() == HttpClient.Redirect.NEVER) { "Credential-bearing requests must not follow redirects" }
    }

    override fun createRequest(
        uri: URI,
        httpMethod: HttpMethod,
    ): ClientHttpRequest {
        require(httpMethod == HttpMethod.GET) { "Provider client supports GET only" }
        return object : ClientHttpRequest {
            private val headers = HttpHeaders()
            private val attributes = mutableMapOf<String, Any>()

            override fun getMethod(): HttpMethod = httpMethod

            override fun getURI(): URI = uri

            override fun getHeaders(): HttpHeaders = headers

            override fun getAttributes(): MutableMap<String, Any> = attributes

            override fun getBody(): OutputStream = throw UnsupportedOperationException("Provider GET has no request body")

            override fun execute(): ClientHttpResponse = send(uri, headers)
        }
    }

    private fun send(
        uri: URI,
        requestHeaders: HttpHeaders,
    ): ClientHttpResponse {
        if (apiKey.isBlank()) providerFailure(MarketDataError.NOT_CONFIGURED)
        // Inject the secret after RestClient logging, interceptors, and observations see the request.
        val separator = if (uri.rawQuery == null) "?" else "&"
        val credentialUri = URI("$uri${separator}apikey=${URLEncoder.encode(apiKey, StandardCharsets.UTF_8)}")
        val request =
            HttpRequest
                .newBuilder(credentialUri)
                .timeout(timeout)
                .GET()
                .apply {
                    // Spring interceptors may add Content-Length: 0; the JDK owns transport headers.
                    val managedHeaders = setOf("connection", "content-length", "expect", "host", "upgrade")
                    requestHeaders.forEach { name, values ->
                        if (name.lowercase(Locale.ROOT) !in managedHeaders) values.forEach { header(name, it) }
                    }
                }.build()
        val pending = client.sendAsync(request) { LimitedBodySubscriber(maxBytes) }
        val response =
            try {
                pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                pending.cancel(true)
                providerFailure(MarketDataError.TIMEOUT)
            } catch (_: InterruptedException) {
                pending.cancel(true)
                Thread.currentThread().interrupt()
                providerFailure(MarketDataError.UNAVAILABLE)
            } catch (error: ExecutionException) {
                when (error.cause) {
                    is HttpTimeoutException -> providerFailure(MarketDataError.TIMEOUT)
                    is ResponseTooLarge -> providerFailure(MarketDataError.MALFORMED_RESPONSE)
                    else -> providerFailure(MarketDataError.UNAVAILABLE)
                }
            }
        return object : ClientHttpResponse {
            private val body = ByteArrayInputStream(response.body())
            private val responseHeaders = HttpHeaders().apply { response.headers().map().forEach { (key, values) -> put(key, values) } }

            override fun getStatusCode(): HttpStatusCode = HttpStatusCode.valueOf(response.statusCode())

            override fun getStatusText(): String = response.statusCode().toString()

            override fun getHeaders(): HttpHeaders = responseHeaders

            override fun getBody(): ByteArrayInputStream = body

            override fun close() {
                body.close()
            }
        }
    }

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
