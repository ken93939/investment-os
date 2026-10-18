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
import kotlin.test.assertFailsWith
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
    fun `tests use PostgreSQL 17`() {
        dataSource.connection.use { connection ->
            assertEquals("PostgreSQL", connection.metaData.databaseProductName)
            assertEquals(17, connection.metaData.databaseMajorVersion)
        }
    }

    @Test
    fun `JPA persists and reloads an entity`() {
        TransactionTemplate(transactionManager).executeWithoutResult { transaction ->
            val saved = repository.saveAndFlush(PersistenceProbe("persistence check"))
            val id = assertNotNull(saved.id)
            entityManager.clear()

            assertEquals("persistence check", repository.findById(id).orElseThrow().label)
            transaction.setRollbackOnly()
        }
    }

    @Test
    fun `failed transaction rolls back flushed writes`() {
        val initialCount = repository.count()

        assertFailsWith<IllegalStateException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                repository.saveAndFlush(PersistenceProbe("must roll back"))
                error("Simulated failure after flush")
            }
        }

        assertEquals(initialCount, repository.count())
    }
}
