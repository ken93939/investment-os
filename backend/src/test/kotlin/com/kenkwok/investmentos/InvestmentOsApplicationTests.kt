package com.kenkwok.investmentos

import jakarta.persistence.EntityManager
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
