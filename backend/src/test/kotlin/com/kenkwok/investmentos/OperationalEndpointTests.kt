package com.kenkwok.investmentos

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.availability.AvailabilityChangeEvent
import org.springframework.boot.availability.ReadinessState
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(EmbeddedPostgresConfiguration::class)
class OperationalEndpointTests {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var context: ConfigurableApplicationContext

    private fun request(path: String): HttpResponse<String> =
        HttpClient.newHttpClient().use { client ->
            client.send(
                HttpRequest
                    .newBuilder(URI("http://127.0.0.1:$port$path"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        }

    @Test
    fun `GIVEN a started application and PostgreSQL WHEN probing health and readiness THEN both report UP without details`() {
        // GIVEN
        val paths = listOf("/health", "/ready")

        // WHEN
        val responses = paths.map(::request)

        // THEN
        responses.forEach { response ->
            assertEquals(200, response.statusCode())
            assertTrue(response.body().contains("\"status\":\"UP\""))
            assertFalse(response.body().contains("components"))
            assertFalse(response.body().contains("details"))
        }
    }

    @Test
    fun `GIVEN an application refusing traffic WHEN probing readiness THEN it returns 503 while liveness stays UP`() {
        // GIVEN
        AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC)
        try {
            // WHEN
            val readiness = request("/ready")
            val liveness = request("/health")

            // THEN
            assertEquals(503, readiness.statusCode())
            assertTrue(readiness.body().contains("OUT_OF_SERVICE"))
            assertEquals(200, liveness.statusCode())
        } finally {
            AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC)
        }
    }
}
