package com.bso.webfluxdemo.infra.database

import com.bso.webfluxdemo.domain.entity.Person
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PersonRepository : R2dbcRepository<Person, UUID>