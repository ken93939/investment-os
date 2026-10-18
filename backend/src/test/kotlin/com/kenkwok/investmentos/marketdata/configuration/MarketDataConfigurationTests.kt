package com.kenkwok.investmentos.marketdata.configuration

import com.kenkwok.investmentos.marketdata.application.MarketDataError
import com.kenkwok.investmentos.marketdata.application.MarketDataException
import com.kenkwok.investmentos.marketdata.application.MarketDataGateway
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarketDataConfigurationTests {
    private val runner =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withConfiguration(
                AutoConfigurations.of(RestClientAutoConfiguration::class.java),
            ).withUserConfiguration(MarketDataConfiguration::class.java)

    @Test
    fun `GIVEN no provider credentials WHEN creating the application configuration THEN startup works with market data disabled`() {
        // GIVEN
        val context = runner.withPropertyValues("market-data.primary=none")

        // WHEN
        context.run { application ->
            val result = runCatching { application.getBean(MarketDataGateway::class.java).instrument("IBM") }

            // THEN
            assertNull(application.startupFailure)
            assertEquals(MarketDataError.NOT_CONFIGURED, assertIs<MarketDataException>(result.exceptionOrNull()).code)
        }
    }

    @Test
    fun `GIVEN an enabled provider without its key WHEN creating the application configuration THEN startup fails safely`() {
        // GIVEN
        val context = runner.withPropertyValues("market-data.primary=alpha-vantage", "market-data.alpha-vantage-api-key=")

        // WHEN
        context.run { application ->
            // THEN
            assertNotNull(application.startupFailure)
        }
    }

    @Test
    fun `GIVEN an unknown provider ID WHEN creating the application configuration THEN startup rejects the configuration`() {
        // GIVEN
        val context = runner.withPropertyValues("market-data.primary=unknown")

        // WHEN
        context.run { application ->
            // THEN
            assertNotNull(application.startupFailure)
        }
    }

    @Test
    fun `GIVEN the imported provider YAML WHEN loading configuration THEN defaults bind without a provider call`() {
        // GIVEN
        val context =
            runner.withPropertyValues(
                "market-data.primary=none",
                "ALPHA_VANTAGE_QUERY_URL=https://www.alphavantage.co/query",
            )

        // WHEN
        context.run { application ->
            val properties = application.getBean(MarketDataProperties::class.java)
            val endpoint = properties.alphaVantage.endpoint()

            // THEN
            assertNull(application.startupFailure)
            assertEquals(URI("https://www.alphavantage.co/query"), endpoint)
            assertEquals(java.time.Duration.ofSeconds(8), properties.requestTimeout)
        }
    }

    @Test
    fun `GIVEN a provider environment URL WHEN loading configuration THEN it overrides the YAML default`() {
        // GIVEN
        val context =
            runner.withPropertyValues(
                "market-data.primary=none",
                "ALPHA_VANTAGE_QUERY_URL=https://staging.example.test/api/query",
            )

        // WHEN
        context.run { application ->
            val endpoint = application.getBean(MarketDataProperties::class.java).alphaVantage.endpoint()

            // THEN
            assertNull(application.startupFailure)
            assertEquals(URI("https://staging.example.test/api/query"), endpoint)
        }
    }

    @Test
    fun `GIVEN a provider configuration profile WHEN loading configuration THEN the profile YAML overrides the base URL`() {
        // GIVEN
        val context = runner.withPropertyValues("market-data.primary=none", "spring.profiles.active=provider-config-test")

        // WHEN
        context.run { application ->
            val endpoint = application.getBean(MarketDataProperties::class.java).alphaVantage.endpoint()

            // THEN
            assertNull(application.startupFailure)
            assertEquals(URI("http://127.0.0.1:9191/stub/query"), endpoint)
        }
    }

    @Test
    fun `GIVEN invalid or credential bearing provider URLs WHEN starting THEN configuration fails without echoing the URL`() {
        // GIVEN
        val urls =
            listOf(
                "",
                "/query",
                "ftp://example.test/query",
                "https://example.test:99999/query",
                "https://secret-token@example.test/query",
                "https://example.test/query?apikey=secret-token",
                "https://example.test/query#secret-token",
                "https://example.test/secret-token invalid",
            )
        urls.forEach { url ->
            val context =
                runner.withPropertyValues(
                    "market-data.primary=alpha-vantage",
                    "market-data.alpha-vantage-api-key=test-key",
                    "market-data.alpha-vantage.query-url=$url",
                )

            // WHEN
            context.run { application ->
                val failure = application.startupFailure

                // THEN
                assertNotNull(failure)
                val messages = generateSequence(failure) { it.cause }.map { it.message.orEmpty() }.toList()
                assertTrue(messages.none { it.contains("secret-token") })
                assertTrue(messages.any { it.contains("query-url must be an absolute HTTP(S) URL") })
            }
        }
    }

    @Test
    fun `GIVEN a configured provider query URL WHEN requesting an instrument THEN the gateway calls that URL`() {
        // GIVEN
        val server =
            com.sun.net.httpserver.HttpServer
                .create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        var query = ""
        server.createContext("/environment/query") { exchange ->
            query = exchange.requestURI.rawQuery
            val body = """{"Symbol":"IBM","Name":"IBM","AssetType":"Common Stock"}""".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val context =
                runner.withPropertyValues(
                    "market-data.primary=alpha-vantage",
                    "market-data.alpha-vantage-api-key=synthetic-test-key",
                    "market-data.alpha-vantage.query-url=http://127.0.0.1:${server.address.port}/environment/query",
                )

            // WHEN
            context.run { application ->
                val result = application.getBean(MarketDataGateway::class.java).instrument("IBM")

                // THEN
                assertNull(application.startupFailure)
                assertEquals("IBM", result.data.symbol.value)
                assertTrue(query.contains("function=OVERVIEW"))
                assertTrue(query.contains("apikey=synthetic-test-key"))
            }
        } finally {
            server.stop(0)
        }
    }
}
