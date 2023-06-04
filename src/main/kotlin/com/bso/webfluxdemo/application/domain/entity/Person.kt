package com.bso.webfluxdemo.application.domain.entity

import java.time.LocalDate
import java.util.*

data class Person(
    val id: UUID,
    val name: String,
    val birthDate: LocalDate,
    val externalId: String? = null
)