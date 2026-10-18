package com.kenkwok.investmentos.marketdata.adapter.out.alphavantage

import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpRequestFactory
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.mock.http.client.MockClientHttpResponse
import org.springframework.web.client.RestClient
import tools.jackson.databind.JsonNode
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.Clock

/** Exercise real DTO decoding and mapping, replacing only HTTP I/O. */
internal fun fixtureClient(
    clock: Clock,
    response: (Map<String, String>) -> JsonNode,
): AlphaVantageRestClient {
    val factory =
        ClientHttpRequestFactory { uri, method ->
            val parameters =
                uri.rawQuery.split("&").associate { parameter ->
                    val (key, value) = parameter.split("=", limit = 2)
                    URLDecoder.decode(key, StandardCharsets.UTF_8) to URLDecoder.decode(value, StandardCharsets.UTF_8)
                }
            MockClientHttpRequest(method, uri).apply {
                setResponse(MockClientHttpResponse(response(parameters).toString().toByteArray(), HttpStatus.OK))
            }
        }
    return AlphaVantageRestClient(RestClient.builder().requestFactory(factory).build(), clock, URI("https://provider.test/query"))
}
