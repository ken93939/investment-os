package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage

import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.OverviewRequest
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.dto.request.SymbolSearchRequest
import com.kenkwok.investmentos.marketdata.application.MarketDataError
import com.kenkwok.investmentos.marketdata.application.MarketDataException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Test
import org.springframework.http.client.observation.ClientRequestObservationContext
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlphaVantageRestClientTests {
    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `GIVEN date based or invalid retry hints WHEN receiving HTTP 429 THEN valid future delays alone are retained`() {
        // GIVEN
        val cases =
            listOf(
                "Thu, 1 Jan 2026 00:01:00 GMT" to Duration.ofSeconds(60),
                "Wed, 31 Dec 2025 23:00:00 GMT" to null,
                "-1" to null,
                "invalid" to null,
            )

        cases.forEach { (hint, expected) ->
            withServer({ exchange ->
                exchange.responseHeaders.set("Retry-After", hint)
                respond(exchange, 429, "rate limited")
            }) { uri, client ->
                val transport = httpClient("test-key", client, Duration.ofSeconds(2), clock, uri)

                // WHEN
                val result = runCatching { transport.overview(OverviewRequest("IBM")) }

                // THEN
                val error = assertIs<MarketDataException>(result.exceptionOrNull())
                assertEquals(MarketDataError.RATE_LIMITED, error.code)
                assertEquals(expected, error.retryAfter)
            }
        }
    }

    private fun httpClient(
        key: String,
        client: HttpClient,
        timeout: Duration,
        clock: Clock,
        uri: URI,
        maxBytes: Int = 8 * 1024 * 1024,
    ): AlphaVantageRestClient =
        AlphaVantageRestClient(
            RestClient.builder().requestFactory(BoundedJdkRequestFactory(key, client, timeout, maxBytes)).build(),
            clock,
            uri,
        )

    private fun withServer(
        handler: (HttpExchange) -> Unit,
        test: (URI, HttpClient) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val executor = Executors.newCachedThreadPool()
        server.executor = executor
        server.createContext("/query", handler)
        server.start()
        try {
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(HttpClient.Redirect.NEVER).build().use { client ->
                test(URI("http://127.0.0.1:${server.address.port}/query"), client)
            }
        } finally {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray()
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    @Test
    fun `GIVEN a successful local provider WHEN making a request THEN query values are encoded and JSON is returned`() {
        // GIVEN
        var query = ""
        withServer({ exchange ->
            query = exchange.requestURI.rawQuery
            respond(exchange, 200, """{"bestMatches":[]}""")
        }) { uri, client ->
            val transport = httpClient("synthetic-test-key", client, Duration.ofSeconds(2), clock, uri)

            // WHEN
            val result = transport.search(SymbolSearchRequest("A&B"))

            // THEN
            assertEquals(emptyList(), result.matches)
            assertTrue(query.contains("keywords=A%26B"))
            assertTrue(query.contains("apikey=synthetic-test-key"))
        }
    }

    @Test
    fun `GIVEN HTTP 429 with retry metadata WHEN calling the provider THEN a typed rate limit is returned without retrying`() {
        // GIVEN
        var calls = 0
        withServer({ exchange ->
            calls++
            exchange.responseHeaders.set("Retry-After", "45")
            respond(exchange, 429, "synthetic-test-key")
        }) { uri, client ->
            val transport = httpClient("synthetic-test-key", client, Duration.ofSeconds(2), clock, uri)

            // WHEN
            val result = runCatching { transport.overview(OverviewRequest("IBM")) }

            // THEN
            val error = assertIs<MarketDataException>(result.exceptionOrNull())
            assertEquals(MarketDataError.RATE_LIMITED, error.code)
            assertEquals(Duration.ofSeconds(45), error.retryAfter)
            assertEquals(1, calls)
            assertTrue(!error.message.orEmpty().contains("synthetic-test-key"))
            assertNull(error.cause)
        }
    }

    @Test
    fun `GIVEN HTTP error responses WHEN making provider requests THEN standard error categories hide response bodies`() {
        // GIVEN
        val cases =
            mapOf(
                401 to MarketDataError.AUTHENTICATION,
                403 to MarketDataError.AUTHENTICATION,
                404 to MarketDataError.NOT_FOUND,
                500 to MarketDataError.UNAVAILABLE,
                400 to MarketDataError.UNSUPPORTED,
            )

        cases.forEach { (status, code) ->
            withServer({ exchange -> respond(exchange, status, "sensitive-provider-body") }) { uri, client ->
                val transport = httpClient("test-key", client, Duration.ofSeconds(2), clock, uri)

                // WHEN
                val result = runCatching { transport.overview(OverviewRequest("IBM")) }

                // THEN
                val error = assertIs<MarketDataException>(result.exceptionOrNull())
                assertEquals(code, error.code)
                assertTrue(!error.message.orEmpty().contains("sensitive-provider-body"))
                assertNull(error.cause)
            }
        }
    }

    @Test
    fun `GIVEN provider errors in HTTP 200 WHEN parsing THEN rate authentication and entitlement errors are recognized`() {
        // GIVEN
        val cases =
            listOf(
                """{"Note":"Our standard API rate limit is 25 requests per day"}""" to MarketDataError.RATE_LIMITED,
                """{"Information":"Invalid API key"}""" to MarketDataError.AUTHENTICATION,
                """{"Information":"This is a premium endpoint"}""" to MarketDataError.UNSUPPORTED,
                """{"Error Message":"Invalid API call"}""" to MarketDataError.UNSUPPORTED,
                "[]" to MarketDataError.MALFORMED_RESPONSE,
                "not-json" to MarketDataError.MALFORMED_RESPONSE,
                "{} {}" to MarketDataError.MALFORMED_RESPONSE,
            )

        cases.forEach { (body, code) ->
            withServer({ exchange -> respond(exchange, 200, body) }) { uri, client ->
                val transport = httpClient("test-key", client, Duration.ofSeconds(2), clock, uri)

                // WHEN
                val result = runCatching { transport.overview(OverviewRequest("IBM")) }

                // THEN
                assertEquals(code, assertIs<MarketDataException>(result.exceptionOrNull()).code)
            }
        }
    }

    @Test
    fun `GIVEN a response exceeding the byte limit WHEN reading the response THEN the transfer is rejected as malformed`() {
        // GIVEN
        withServer({ exchange -> respond(exchange, 200, "x".repeat(1024)) }) { uri, client ->
            val transport = httpClient("test-key", client, Duration.ofSeconds(2), clock, uri, maxBytes = 64)

            // WHEN
            val result = runCatching { transport.overview(OverviewRequest("IBM")) }

            // THEN
            assertEquals(MarketDataError.MALFORMED_RESPONSE, assertIs<MarketDataException>(result.exceptionOrNull()).code)
        }
    }

    @Test
    fun `GIVEN a stalled response body WHEN its deadline expires THEN the request is cancelled with a typed timeout`() {
        // GIVEN
        val release = CountDownLatch(1)
        try {
            withServer({ exchange ->
                exchange.sendResponseHeaders(200, 100)
                release.await(1, TimeUnit.SECONDS)
                exchange.close()
            }) { uri, client ->
                val transport = httpClient("test-key", client, Duration.ofMillis(200), clock, uri)

                // WHEN
                val result = runCatching { transport.overview(OverviewRequest("IBM")) }

                // THEN
                assertEquals(MarketDataError.TIMEOUT, assertIs<MarketDataException>(result.exceptionOrNull()).code)
                release.countDown()
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `GIVEN no API key WHEN making a provider request THEN configuration fails without a network call`() {
        // GIVEN
        var calls = 0
        withServer({ exchange ->
            calls++
            respond(exchange, 200, "{}")
        }) { uri, client ->
            val transport = httpClient("", client, Duration.ofSeconds(2), clock, uri)

            // WHEN
            val result = runCatching { transport.overview(OverviewRequest("IBM")) }

            // THEN
            assertEquals(MarketDataError.NOT_CONFIGURED, assertIs<MarketDataException>(result.exceptionOrNull()).code)
            assertEquals(0, calls)
        }
    }

    @Test
    fun `GIVEN a redirect response WHEN making a credential bearing request THEN the redirect is not followed`() {
        // GIVEN
        var calls = 0
        withServer({ exchange ->
            calls++
            exchange.responseHeaders.set("Location", "/query?redirected=true")
            respond(exchange, 302, "redirect")
        }) { uri, client ->
            val transport = httpClient("test-key", client, Duration.ofSeconds(2), clock, uri)

            // WHEN
            val result = runCatching { transport.overview(OverviewRequest("IBM")) }

            // THEN
            assertEquals(MarketDataError.UNAVAILABLE, assertIs<MarketDataException>(result.exceptionOrNull()).code)
            assertEquals(1, calls)
        }
    }

    @Test
    fun `GIVEN a chunked response exceeding the byte limit WHEN reading THEN transfer fails without trusting content length`() {
        // GIVEN
        withServer({ exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { it.write("x".repeat(1024).toByteArray()) }
        }) { uri, client ->
            val providerClient = httpClient("test-key", client, Duration.ofSeconds(2), clock, uri, maxBytes = 64)

            // WHEN
            val result = runCatching { providerClient.overview(OverviewRequest("IBM")) }

            // THEN
            assertEquals(MarketDataError.MALFORMED_RESPONSE, assertIs<MarketDataException>(result.exceptionOrNull()).code)
        }
    }

    @Test
    fun `GIVEN a trickling body WHEN total deadline expires THEN timeout occurs and the client remains usable`() {
        // GIVEN
        val release = CountDownLatch(1)
        val calls =
            java.util.concurrent.atomic
                .AtomicInteger()
        try {
            withServer({ exchange ->
                if (calls.incrementAndGet() == 1) {
                    exchange.sendResponseHeaders(200, 0)
                    try {
                        exchange.responseBody.use { body ->
                            while (!release.await(30, TimeUnit.MILLISECONDS)) {
                                body.write(' '.code)
                                body.flush()
                            }
                        }
                    } catch (_: java.io.IOException) {
                        // Cancellation closes the first response while another request can proceed.
                    }
                } else {
                    respond(exchange, 200, """{"Symbol":"IBM"}""")
                }
            }) { uri, client ->
                val providerClient = httpClient("test-key", client, Duration.ofMillis(400), clock, uri)
                val start = System.nanoTime()

                // WHEN
                val result = runCatching { providerClient.overview(OverviewRequest("IBM")) }
                val elapsed = Duration.ofNanos(System.nanoTime() - start)
                release.countDown()
                val next = providerClient.overview(OverviewRequest("IBM"))

                // THEN
                assertEquals(MarketDataError.TIMEOUT, assertIs<MarketDataException>(result.exceptionOrNull()).code)
                assertTrue(elapsed < Duration.ofSeconds(2))
                assertEquals("IBM", next.symbol)
                assertEquals(2, calls.get())
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `GIVEN a query credential WHEN RestClient observes a request THEN only the wire request contains the key`() {
        // GIVEN
        var wireQuery = ""
        var interceptedUri = ""
        val observedUris = mutableListOf<String>()
        val registry =
            io.micrometer.observation.ObservationRegistry
                .create()
        registry.observationConfig().observationHandler(
            object : ObservationHandler<ClientRequestObservationContext> {
                override fun supportsContext(context: Observation.Context): Boolean = context is ClientRequestObservationContext

                override fun onStop(context: ClientRequestObservationContext) {
                    observedUris += context.carrier?.uri.toString()
                    observedUris += context.highCardinalityKeyValues.map { it.value }
                }
            },
        )
        withServer({ exchange ->
            wireQuery = exchange.requestURI.rawQuery
            respond(exchange, 200, """{"Symbol":"IBM"}""")
        }) { uri, client ->
            val restClient =
                RestClient
                    .builder()
                    .observationRegistry(registry)
                    .requestInterceptor { request, body, execution ->
                        interceptedUri = request.uri.toString()
                        execution.execute(request, body)
                    }.requestFactory(BoundedJdkRequestFactory("synthetic-secret", client, Duration.ofSeconds(2)))
                    .build()
            val providerClient = AlphaVantageRestClient(restClient, clock, uri)

            // WHEN
            val result = providerClient.overview(OverviewRequest("IBM"))

            // THEN
            assertEquals("IBM", result.symbol)
            assertTrue(wireQuery.contains("apikey=synthetic-secret"))
            assertTrue(!interceptedUri.contains("synthetic-secret") && !interceptedUri.contains("apikey"))
            assertTrue(observedUris.isNotEmpty())
            assertTrue(observedUris.none { it.contains("synthetic-secret") || it.contains("apikey") })
        }
    }
}
