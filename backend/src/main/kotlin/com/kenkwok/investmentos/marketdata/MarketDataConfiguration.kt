package com.kenkwok.investmentos.marketdata

import com.kenkwok.investmentos.marketdata.alphavantage.AlphaVantageHttpTransport
import com.kenkwok.investmentos.marketdata.alphavantage.AlphaVantageProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
        @Qualifier("marketDataHttpClient") client: HttpClient,
    ): FinancialDataProvider {
        require(properties.primary != "alpha-vantage" || properties.alphaVantageApiKey.isNotBlank()) {
            "Set ALPHA_VANTAGE_API_KEY before selecting alpha-vantage"
        }
        val clock = Clock.systemUTC()
        return AlphaVantageProvider(
            AlphaVantageHttpTransport(properties.alphaVantageApiKey, client, properties.requestTimeout, clock),
            clock,
        )
    }

    @Bean
    fun marketDataGateway(
        properties: MarketDataProperties,
        providers: List<FinancialDataProvider>,
    ): MarketDataGateway = MarketDataGateway(properties.primary, providers)
}
