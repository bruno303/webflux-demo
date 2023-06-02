package com.bso.webfluxdemo.application.repository

import com.bso.webfluxdemo.application.domain.entity.Person
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PersonRepository : R2dbcRepository<Person, UUID>