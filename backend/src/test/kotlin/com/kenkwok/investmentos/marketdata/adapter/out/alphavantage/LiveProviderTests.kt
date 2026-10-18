package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage

import com.kenkwok.investmentos.marketdata.application.MarketDataGateway
import com.kenkwok.investmentos.marketdata.configuration.MarketDataConfiguration
import com.kenkwok.investmentos.marketdata.domain.AssetType
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.util.Currency
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("live-provider")
class LiveProviderTests {
    @Test
    fun `GIVEN valid provider credentials WHEN fetching IBM metadata THEN a sourced US stock is returned`() {
        // GIVEN
        require(!System.getenv("ALPHA_VANTAGE_API_KEY").isNullOrBlank()) { "Set ALPHA_VANTAGE_API_KEY to run the live provider test" }
        val runner =
            ApplicationContextRunner()
                .withInitializer(ConfigDataApplicationContextInitializer())
                .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration::class.java))
                .withUserConfiguration(MarketDataConfiguration::class.java)
                .withPropertyValues("market-data.primary=alpha-vantage")

        // WHEN
        runner.run { context ->
            val outcome = runCatching { context.getBean(MarketDataGateway::class.java).instrument("IBM") }

            // THEN
            assertNull(context.startupFailure)
            val result = outcome.getOrThrow()
            assertEquals("IBM", result.data.symbol.value)
            assertEquals(AssetType.STOCK, result.data.assetType)
            assertEquals(Currency.getInstance("USD"), result.data.currency)
            assertEquals("US", result.data.country)
            assertEquals("alpha-vantage", result.provenance.provider)
            assertTrue(result.data.name.isNotBlank())
        }
    }
}
