package com.kenkwok.investmentos

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import org.springframework.data.jpa.repository.JpaRepository

// Test-only mapping: the scaffold does not introduce a financial domain schema.
@Entity
class PersistenceProbe(
    @Column(nullable = false)
    var label: String,
    @Id
    @GeneratedValue
    var id: Long? = null,
)

interface PersistenceProbeRepository : JpaRepository<PersistenceProbe, Long>
