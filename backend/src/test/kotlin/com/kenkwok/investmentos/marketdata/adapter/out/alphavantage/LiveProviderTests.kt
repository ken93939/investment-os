package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage

import com.kenkwok.investmentos.marketdata.domain.AssetType
import com.kenkwok.investmentos.marketdata.domain.Symbol
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.util.Currency
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Tag("live-provider")
class LiveProviderTests {
    @Test
    fun `GIVEN valid provider credentials WHEN fetching IBM metadata THEN a sourced US stock is returned`() {
        // GIVEN
        val key = requireNotNull(System.getenv("ALPHA_VANTAGE_API_KEY"))
        val clock = Clock.systemUTC()
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build().use { client ->
            val provider =
                AlphaVantageAdapter(
                    AlphaVantageRestClient(
                        RestClient.builder().requestFactory(BoundedJdkRequestFactory(key, client, Duration.ofSeconds(8))).build(),
                        clock,
                    ),
                    clock,
                )

            // WHEN
            val result = provider.instrument(Symbol.parse("IBM"))

            // THEN
            assertEquals("IBM", result.data.symbol.value)
            assertEquals(AssetType.STOCK, result.data.assetType)
            assertEquals(Currency.getInstance("USD"), result.data.currency)
            assertEquals("US", result.data.country)
            assertEquals("alpha-vantage", result.provenance.provider)
            assertTrue(result.data.name.isNotBlank())
        }
    }
}
