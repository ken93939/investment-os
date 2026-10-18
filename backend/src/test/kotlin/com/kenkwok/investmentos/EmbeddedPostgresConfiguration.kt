package com.kenkwok.investmentos

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import javax.sql.DataSource

@TestConfiguration(proxyBeanMethods = false)
class EmbeddedPostgresConfiguration {
    @Bean(destroyMethod = "close")
    fun embeddedPostgres(): EmbeddedPostgres =
        EmbeddedPostgres
            .builder()
            // Avoid inheriting a host locale that initdb cannot resolve.
            .setLocaleConfig("locale", "C")
            // Spring must close JPA before stopping PostgreSQL.
            .setRegisterShutdownHook(false)
            .setPort(0)
            .start()

    @Bean
    fun dataSource(embeddedPostgres: EmbeddedPostgres): DataSource = embeddedPostgres.postgresDatabase
}
