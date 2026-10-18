package com.kenkwok.investmentos.marketdata.configuration

import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.AlphaVantageAdapter
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.AlphaVantageRestClient
import com.kenkwok.investmentos.marketdata.adapter.out.alphavantage.BoundedJdkRequestFactory
import com.kenkwok.investmentos.marketdata.application.MarketDataGateway
import com.kenkwok.investmentos.marketdata.application.port.out.FinancialDataProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient
import java.net.URI
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration

// Plain classes intentionally avoid generating a toString that contains the API key.
@ConfigurationProperties("market-data")
class MarketDataProperties {
    var primary: String = "none"
    var requestTimeout: Duration = Duration.ofSeconds(8)
    var connectTimeout: Duration = Duration.ofSeconds(2)
    var alphaVantageApiKey: String = ""
    var alphaVantage: AlphaVantageEndpointProperties = AlphaVantageEndpointProperties()
}

class AlphaVantageEndpointProperties {
    var queryUrl: String = ""

    fun endpoint(): URI {
        val uri = runCatching { URI(queryUrl) }.getOrNull()
        require(
            uri != null &&
                uri.scheme in setOf("https", "http") &&
                !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                (uri.port == -1 || uri.port in 1..65535),
        ) {
            "market-data.alpha-vantage.query-url must be an absolute HTTP(S) URL without credentials, query parameters, or fragment"
        }
        return uri
    }
}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MarketDataProperties::class)
class MarketDataConfiguration {
    @Bean(destroyMethod = "close")
    fun marketDataHttpClient(properties: MarketDataProperties): HttpClient {
        require(properties.connectTimeout in Duration.ofMillis(100)..properties.requestTimeout) { "Invalid provider connection timeout" }
        return HttpClient
            .newBuilder()
            .connectTimeout(properties.connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    }

    @Bean
    fun alphaVantageProvider(
        properties: MarketDataProperties,
        builder: RestClient.Builder,
        @Qualifier("marketDataHttpClient") client: HttpClient,
    ): FinancialDataProvider {
        require(properties.primary != "alpha-vantage" || properties.alphaVantageApiKey.isNotBlank()) {
            "Set ALPHA_VANTAGE_API_KEY before selecting alpha-vantage"
        }
        val clock = Clock.systemUTC()
        return AlphaVantageAdapter(
            AlphaVantageRestClient(
                builder.requestFactory(BoundedJdkRequestFactory(properties.alphaVantageApiKey, client, properties.requestTimeout)).build(),
                clock,
                properties.alphaVantage.endpoint(),
            ),
            clock,
        )
    }

    @Bean
    fun marketDataGateway(
        properties: MarketDataProperties,
        providers: List<FinancialDataProvider>,
    ): MarketDataGateway = MarketDataGateway(properties.primary, providers)
}
