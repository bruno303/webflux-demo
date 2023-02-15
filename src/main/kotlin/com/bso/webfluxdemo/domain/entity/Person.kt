package com.bso.webfluxdemo.domain.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.LocalDate
import java.util.*

@Entity
data class Person(
    @Id
    val id: UUID,
    val name: String,
    val birthDate: LocalDate,
    val externalId: String? = null
)