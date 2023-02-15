package com.bso.webfluxdemo.service

import com.bso.webfluxdemo.domain.entity.Person
import com.bso.webfluxdemo.infra.database.PersonRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.util.*

@Service
class CustomFlowProcessor(
    private val personRepository: PersonRepository,
    private val externalService: ExternalService
) {
    private val logger: Logger by lazy { LoggerFactory.getLogger(this::class.java) }

    @Transactional
    operator fun invoke() : Mono<Person> {
        val person = Person(
            id = UUID.randomUUID(),
            name = "Person Xpto",
            birthDate = LocalDate.of(1995, 12, 1)
        )

        return externalService.getExternalIdFromExternalService()
            .flatMap { Mono.just(person.copy(externalId = it)) }
            .flatMap { personRepository.save(it) }
            .doOnNext { logger.info("Person created: {}", it) }
            .doOnNext { throw IllegalStateException("Teste") }
    }

    @Transactional
    suspend fun execute(): Person {
        val person = Person(
            id = UUID.randomUUID(),
            name = "Person Xpto2",
            birthDate = LocalDate.of(1995, 12, 1)
        ).let { person ->
            externalService.getExternalIdFromExternalService().awaitSingle().let {
                person.copy(externalId = it)
            }
        }



        return personRepository.save(person).awaitSingle().let {
            logger.info("Person created: {}", it)
            throw IllegalStateException("Teste")
            it
        }
    }
}