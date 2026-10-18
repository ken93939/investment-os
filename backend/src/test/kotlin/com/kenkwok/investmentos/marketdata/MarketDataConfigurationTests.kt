package com.kenkwok.investmentos.marketdata

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MarketDataConfigurationTests {
    private val runner = ApplicationContextRunner().withUserConfiguration(MarketDataConfiguration::class.java)

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
}
