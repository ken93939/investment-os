package com.kenkwok.investmentos

import jakarta.persistence.EntityManager
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.exception.FlywayValidateException
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@SpringBootTest
@ActiveProfiles("test")
@Import(EmbeddedPostgresConfiguration::class)
class InvestmentOsApplicationTests {
    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var repository: PersistenceProbeRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var flyway: Flyway

    @Test
    fun `GIVEN a fresh PostgreSQL database WHEN startup and repeat migration run THEN the baseline is applied once`() {
        // GIVEN
        val applied = flyway.info().applied()

        // WHEN
        val repeated = flyway.migrate()

        // THEN
        assertEquals(listOf("1"), applied.mapNotNull { it.version?.version })
        assertEquals(0, repeated.migrationsExecuted)
        assertEquals(true, flyway.validateWithResult().validationSuccessful)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                val query = "SELECT count(*) FROM investment_os.flyway_schema_history WHERE success AND version = '1'"
                statement.executeQuery(query).use { rows ->
                    rows.next()
                    assertEquals(1, rows.getInt(1))
                }
            }
        }
    }

    @Test
    fun `GIVEN an applied migration WHEN its checksum differs THEN validation fails before schema changes`() {
        // GIVEN
        val changed =
            Flyway
                .configure()
                .dataSource(dataSource)
                .schemas("investment_os")
                .locations("classpath:db/changed-migration")
                .load()

        // WHEN
        val result = runCatching { changed.validate() }

        // THEN
        assertIs<FlywayValidateException>(result.exceptionOrNull())
        assertEquals(true, flyway.validateWithResult().validationSuccessful)
    }

    @Test
    fun `GIVEN the embedded datasource WHEN reading database metadata THEN the database is PostgreSQL 17`() {
        // GIVEN
        dataSource.connection.use { connection ->
            // WHEN
            val metadata = connection.metaData

            // THEN
            assertEquals("PostgreSQL", metadata.databaseProductName)
            assertEquals(17, metadata.databaseMajorVersion)
        }
    }

    @Test
    fun `GIVEN a new entity WHEN saving and reloading it THEN its generated ID and label are preserved`() {
        // GIVEN
        val probe = PersistenceProbe("persistence check")
        val transactionTemplate = TransactionTemplate(transactionManager)

        transactionTemplate.executeWithoutResult { transaction ->
            // WHEN
            val saved = repository.saveAndFlush(probe)
            entityManager.clear()
            val reloaded = repository.findById(requireNotNull(saved.id)).orElseThrow()

            // THEN
            assertNotNull(reloaded.id)
            assertEquals(saved.id, reloaded.id)
            assertEquals("persistence check", reloaded.label)
            transaction.setRollbackOnly()
        }
    }

    @Test
    fun `GIVEN a new entity WHEN its transaction fails after flushing THEN the insert is rolled back`() {
        // GIVEN
        val initialCount = repository.count()
        val probe = PersistenceProbe("must roll back")
        val transactionTemplate = TransactionTemplate(transactionManager)

        // WHEN
        val result =
            runCatching {
                transactionTemplate.executeWithoutResult {
                    repository.saveAndFlush(probe)
                    error("Simulated failure after flush")
                }
            }

        // THEN
        assertIs<IllegalStateException>(result.exceptionOrNull())
        assertEquals(initialCount, repository.count())
    }
}
